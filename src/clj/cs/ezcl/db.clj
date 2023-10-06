(ns cs.ezcl.db
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [honey.sql :as sql]
    [integrant.core :as ig]
    [next.jdbc :as jdbc]
    [next.jdbc.date-time]
    [next.jdbc.result-set :as rs]))

(defn kw-str
  [k]
  (subs (str k) 1))

(defn decode-jsonb
  [m kw-ns]
  (into {}
    (map (fn [[k v]]
           (let [canon-k (keyword (name kw-ns) (name k))]
             [canon-k v])))
    m))

(defn json-pgobject->data
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (json/read-str value :key-fn keyword) {:pgtype type}))
      value)))

(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (json-pgobject->data v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (json-pgobject->data v)))

(defn update-many-sql
  [query-map {:keys [values]}]
  (let [ks (reduce (fn [acc m] (apply conj acc (keys m))) #{} values)]
    (assoc query-map
      :set (into {} (map (fn [k] [(keyword (name k)) (keyword (str "v." (name k)))])) ks)
      :from [[[{:values (map (fn [m] (map (fn [k] (m k)) ks)) values)}] [:v [:raw "(" (str/join ", " (map name ks)) ")"]]]])))

(defmethod ig/init-key :postgres/datasource
  [_ {:postgres/keys [db-spec]}]
  (jdbc/get-datasource db-spec))

(defn tenant-schema
  [tenant-id & {:keys [table]}]
  (cond-> (str "tenant_" tenant-id)
    table (str "." (name table))
    true keyword))

(defn create-tenant-schema!
  [ds & {:keys [tenant-id]}]
  (jdbc/execute-one! ds [(str "CREATE SCHEMA IF NOT EXISTS " (name (tenant-schema tenant-id)))]))

(defn delete-tenant-schema!
  [ds & {:keys [tenant-id]}]
  (jdbc/execute! ds [(str "DROP SCHEMA IF EXISTS " (name (tenant-schema tenant-id)) " CASCADE")]))

(defn create-zone-table-sql
  [& {tenant-id :tenant/id}]
  (sql/format {:create-table
               [(tenant-schema tenant-id :table :zone)
                :if-not-exists
                {:with-columns [[:id :varchar [:not nil] :primary-key]
                                [:name :varchar [:not nil]]
                                [:creation_date :timestamp [:not nil]]]}]}
    {:pretty true}))

(defn create-instance-group-table-sql
  [& {tenant-id :tenant/id}]
  (sql/format {:create-table
               [(tenant-schema tenant-id :table :instance-group)
                :if-not-exists
                {:with-columns [[:id :varchar [:not nil] :primary-key]
                                [:creation-date :timestamp [:not nil]]
                                [:name :varchar [:not nil]]
                                [:container :varchar [:not nil]]
                                [:run_flags :varchar [:not nil]]
                                [:zone_id :varchar [:references (tenant-schema tenant-id :table :zone) :id]]
                                [:instance_group_template_id :varchar]
                                [:github_username :varchar]]}]}
    {:pretty true}))

(defn create-endpoint-table-sql
  [& {tenant-id :tenant/id}]
  (sql/format {:create-table
               [(tenant-schema tenant-id :table :endpoint)
                :if-not-exists
                {:with-columns [[:id :varchar [:not nil] :primary-key]
                                [:instance_group_id :varchar
                                 [:references (tenant-schema tenant-id :table :instance-group) :id]
                                 :on-delete-cascade]
                                [:creation_date :timestamp [:not nil]]
                                [:name :varchar [:not nil]]
                                [:from_port :integer [:not nil]]
                                [:to_port :integer [:not nil]]]}]}
    {:pretty true}))

(defn init-tenant!
  [ds & {:keys [tenant-id]}]
  (jdbc/with-transaction [tx ds]
    (create-tenant-schema! tx :tenant-id tenant-id)
    (jdbc/execute-one! tx (create-zone-table-sql :tenant/id tenant-id))
    (jdbc/execute-one! tx (create-instance-group-table-sql :tenant/id tenant-id))
    (jdbc/execute-one! tx (create-endpoint-table-sql :tenant/id tenant-id))))

(defn delete-zone!
  [ds & {tenant-id :tenant/id
         zone-id   :zone/id}]
  (jdbc/execute-one! ds
    (sql/format {:delete-from (tenant-schema tenant-id :table :zone)
                 :where       [:= zone-id :zone/id]}
      {:pretty true})))

(defn list-zones!
  [ds & {tenant-id :tenant/id}]
  (jdbc/execute! ds
    (sql/format {:select :* :from (tenant-schema tenant-id :table :zone)} {:pretty true})))

(defn insert-instance-group!
  [ds & {tenant-id :tenant/id
         :keys     [instance-group]}]
  (jdbc/with-transaction [tx ds]
    (jdbc/execute-one! tx
      (sql/format {:insert-into (tenant-schema tenant-id :table :instance-group)
                   :values      [(dissoc instance-group :instance_group/endpoints)]}
        {:pretty true}))
    (when-let [endpoints (some->>
                           (:instance_group/endpoints instance-group)
                           (map (fn [endpoint]
                                  (assoc endpoint :endpoint/instance_group_id (:instance_group/id instance-group)))))]
      (jdbc/execute-one! tx
        (sql/format {:insert-into (tenant-schema tenant-id :table :endpoint)
                     :values      endpoints}
          {:pretty true})))))

(defn update-instance-group!
  [ds & {tenant-id :tenant/id
         :keys     [instance-group]}]
  (jdbc/with-transaction [tx ds]
    (jdbc/execute-one! tx
      (sql/format {:update (tenant-schema tenant-id :table :instance-group)
                   :set    (dissoc instance-group
                             :instance_group/id
                             :instance_group/endpoints)
                   :where  [:= :id (:instance_group/id instance-group)]}
        {:pretty true}))
    (when-let [endpoints (some->>
                           (:instance_group/endpoints instance-group)
                           (map (fn [endpoint]
                                  (-> endpoint
                                    (assoc :endpoint/instance_group_id (:instance_group/id instance-group))))))]
      (jdbc/execute-one! tx
        (-> {:update [(tenant-schema tenant-id :table :endpoint) :endpoint]
             :where  [:= :endpoint.id :v.id]}
          (update-many-sql {:values endpoints})
          sql/format)))))

(defn delete-instance-group!
  [ds & {tenant-id         :tenant/id
         instance-group-id :instance_group/id}]
  (jdbc/execute-one! ds
    (sql/format {:delete-from (tenant-schema tenant-id :table :instance-group)
                 :where       [:= instance-group-id :instance_group/id]}
      {:pretty true})))

(defn list-instance-groups!
  [ds & {tenant-id         :tenant/id
         instance-group-id :instance_group/id}]
  (->> (sql/format
         (cond-> {:select   [:instance_group.*
                             [[:to_jsonb :zone.*] (kw-str :instance_group/zone)]
                             [[:json_agg [:to_jsonb :endpoint.*]] (kw-str :instance_group/endpoints)]]
                  :from     [[(tenant-schema tenant-id :table :instance-group) :instance_group]]
                  :join     [[(tenant-schema tenant-id :table :zone) :zone]
                             [:= :instance_group.zone-id :zone.id]
                             [(tenant-schema tenant-id :table :endpoint) :endpoint]
                             [:= :endpoint.instance_group_id :instance_group.id]]
                  :group-by [:instance-group.id :zone.id]}
           instance-group-id
           (assoc :where [:= :instance_group.id instance-group-id]))
         {:pretty true})
    (jdbc/execute! ds)
    (mapv (fn [instance-group]
            (cond-> instance-group
              true (update :instance_group/zone decode-jsonb :zone)
              (:instance_group/endpoints instance-group)
              (update :instance_group/endpoints (fn [endpoints] (mapv #(decode-jsonb % :endpoint) endpoints))))))))

(defn describe-instance-group!
  [ds & {:as argm}]
  (first (list-instance-groups! ds argm)))

(comment
  (def db-spec {:dbtype "postgresql"
                :dbname "ezcl"
                :user   "postgres"})
  (def ds (jdbc/get-datasource db-spec))

  (init-tenant! ds :tenant-id "1")
  (init-tenant! ds :tenant-id "local")
  (delete-tenant-schema! ds :tenant-id "1")
  )
