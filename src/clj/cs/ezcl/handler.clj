(ns cs.ezcl.handler
  (:require
    [cs.ezcl.db :as db]
    [cs.ezcl.middleware :as middleware]
    [honey.sql :as sql]
    [integrant.core :as ig]
    [kwill.anomkit :as ak]
    [kwill.aws-api :as aws]
    [nano-id.core :as nano-id]
    [next.jdbc :as jdbc]
    [reitit.coercion.malli :as malli]
    [reitit.core :as reitit]
    [reitit.ring :as ring]
    [reitit.ring.coercion :as coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [ring.util.http-response :as http-response]
    [ring.util.response :as resp])
  (:import (java.util Date)))


(defn tenant-id-from-request
  [request]
  "1")

(defn list-instance-groups-handler
  [request]
  (let [{:postgres/keys [datasource]} (:ezcl/ctx request)
        instances (db/list-instance-groups! datasource
                    :tenant/id (tenant-id-from-request request))]
    {:status 200
     :body   {:data instances}}))

(defn describe-instance-group-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        instance-group (db/describe-instance-group! datasource
                         :tenant/id (tenant-id-from-request request)
                         :instance_group/id (:instance_group/id body))]
    {:status 200
     :body   {:data instance-group}}))

(defn create-instance-group-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        tenant-id (tenant-id-from-request request)
        ig-id (nano-id/nano-id)
        ig (-> body
             (assoc
               :instance_group/id ig-id
               :instance_group/creation_date (java.util.Date.))
             (cond->
               (:instance_group/endpoints body)
               (assoc
                 :instance_group/endpoints
                 (mapv (fn [endpoint]
                         (assoc endpoint
                           :endpoint/id (nano-id/nano-id)
                           :endpoint/creation_date (java.util.Date.)))
                   (:instance_group/endpoints body)))))
        _ (db/insert-instance-group! datasource
            :tenant/id tenant-id
            :instance-group ig)
        instance-group (db/describe-instance-group! datasource
                         :tenant/id tenant-id
                         :instance_group/id ig-id)
        ;; TODO: provisioning
        ]
    {:status 200
     :body   {:instance-group instance-group}}))

(defn update-instance-group-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        tenant-id (tenant-id-from-request request)
        _ (db/update-instance-group! datasource
            :tenant/id tenant-id
            :instance-group body)
        instance-group (db/describe-instance-group! datasource
                         :tenant/id tenant-id
                         :instance_group/id (:instance_group/id body))
        ;; TODO: insert update
        ]
    {:status 200
     :body   {:instance-group body}}))

(defn retry-instance-group-handler
  [request]
  ;; TODO:
  {:status 200
   :body   {}})

(defn describe-instance-group-status-handler
  [request]
  ;; TODO: fill in
  {:status 200
   :body   {:data {:deployment/status :deployment.status/succeeded}}})

(defn describe-instance-group-instances-handler
  [request]
  (let [{:postgres/keys [datasource]} (:ezcl/ctx request)
        {:keys [parameters]} request
        {:keys [body]} parameters
        tenant-id (tenant-id-from-request request)
        {instance-group-id :instance_group/id} (db/describe-instance-group! datasource
                                                 :tenant/id tenant-id
                                                 :instance_group/id (:instance_group/id body))
        ec2-client (aws/client {:api :ec2})
        describe-response (-> ec2-client
                            (aws/invoke
                              {:op      :DescribeInstances
                               :request {:Filters [{:Name   "tag:ezcl:instanceGroupId"
                                                    :Values [instance-group-id]}
                                                   {:Name   "instance-state-name"
                                                    :Values ["running"]}]}})
                            ak/?!)
        instances (for [{:keys [Instances]} (:Reservations describe-response)
                        {:keys [InstanceId PublicIpAddress]} Instances]
                    {:instance/instance_id       InstanceId
                     :instance/public_ip_address PublicIpAddress})]
    {:status 200
     :body   {:data {:instance_group/instances instances}}}))

(defn get-instance-group-logs-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {instance-group-id :instance_group/id} body
        logs-client (aws/client {:api :logs :region "us-west-2"})
        {:keys [events]} (-> logs-client
                           (aws/invoke {:op      :FilterLogEvents
                                        :request {:logGroupName (format "%s.log" instance-group-id)
                                                  :limit        20
                                                  :startTime    (- (inst-ms (java.util.Date.)) (* 1000 60 5))}})
                           (ak/?!))
        logs (map (fn [{:keys [timestamp message eventId]}]
                    {:log_event/id        eventId
                     :log_event/timestamp (Date. ^long timestamp)
                     :log_event/message   message}) events)]
    {:status 200
     :body   {:data logs}}))

(defn delete-instance-group-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        {instance-group-id :instance_group/id} body
        _ (db/delete-instance-group! datasource
            :tenant/id (tenant-id-from-request request)
            :instance_group/id instance-group-id)]
    {:status 200
     :body   {}}))

(defn ListZones-handler
  [request]
  (let [{:postgres/keys [datasource]} (:ezcl/ctx request)
        zones (db/list-zones! datasource
                :tenant/id (tenant-id-from-request request))]
    {:status 200
     :body   {:data zones}}))

(defn describe-zone-deployment-handler
  [request]
  ;; TODO: fill in
  {:status 200
   :body   {:data {:deployment/status :deployment.status/succeeded}}})


(defn create-zone-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        zone-id (nano-id/nano-id)
        canon-zone {:zone/name          (:zone/name body)
                    :zone/id            zone-id
                    :zone/creation-date (java.util.Date.)}
        _ (jdbc/execute-one! datasource
            (sql/format {:insert-into (db/tenant-schema "1" :table :zone)
                         :values      [canon-zone]}))]
    {:status 200
     :body   {:zone canon-zone}}))

(defn delete-zone-handler
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:postgres/keys [datasource]} (:ezcl/ctx request)
        {zone-id :zone/id} body
        ;; TODO: insert external delete
        _ (db/delete-zone! datasource
            :tenant/id (tenant-id-from-request request)
            :zone/id zone-id)]
    {:status 200
     :body   {}}))

(defn route-data
  [{:authentication/keys [jwks-data]}]
  {:coercion   (malli/create (dissoc malli/default-options :compile :strip-extra-keys))
   :muuntaja   middleware/instance
   :swagger    {:id ::api}
   :middleware [;; query-params & form-params
                parameters/parameters-middleware
                ;; content-negotiation
                muuntaja/format-negotiate-middleware
                ;; encoding response body
                muuntaja/format-response-middleware
                ;; exception handling
                coercion/coerce-exceptions-middleware
                ;; decoding request body
                muuntaja/format-request-middleware
                ;; coercing response bodys
                coercion/coerce-response-middleware
                ;; coercing request parameters
                coercion/coerce-request-middleware
                ;; exception handling
                middleware/wrap-exception
                ;; Authentication
                [middleware/wrap-authentication jwks-data]]})

(defn api-routes [_opts]
  [[""
    ["" {:authenticated true}
     ["/CreateInstanceGroup" {:post       create-instance-group-handler
                              :parameters {:body some?}}]
     ["/UpdateInstanceGroup" {:post       update-instance-group-handler
                              :parameters {:body some?}}]
     ["/DescribeInstanceGroup" {:post       describe-instance-group-handler
                                :parameters {:body some?}}]
     ["/GetInstanceGroupLogs" {:post       get-instance-group-logs-handler
                               :parameters {:body some?}}]
     ["/ListInstanceGroups" {:post list-instance-groups-handler}]
     ["/RetryInstanceGroup" {:post       retry-instance-group-handler
                             :parameters {:body some?}}]
     ["/DeleteInstanceGroup" {:post       delete-instance-group-handler
                              :parameters {:body some?}}]
     ["/DescribeInstanceGroupStatus" {:post       describe-instance-group-status-handler
                                      :parameters {:body some?}}]
     ["/DescribeInstanceGroupInstances" {:post       describe-instance-group-instances-handler
                                         :parameters {:body some?}}]

     ["/CreateZone" {:post       create-zone-handler
                     :parameters {:body some?}
                     :responses  {200 {:body map?}}}]
     ["/DeleteZone" {:post       delete-zone-handler
                     :parameters {:body some?}
                     :responses  {200 {:body map?}}}]
     ["/DescribeZoneStatus" {:post       describe-zone-deployment-handler
                             :parameters {:body some?}}]
     ["/ListZones" {:post ListZones-handler}]

     ["/*" {:default-handler? true
            :handler          (ring/create-default-handler)}]]]])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path (route-data opts) (api-routes opts)])

(defn spa-handler
  [request]
  (or
    (resp/resource-response (:uri request) {:root "public"})
    (-> (resp/resource-response "index.html" {:root "public"})
      (resp/content-type "text/html"))))

(defn ring-handler
  [{:keys [router] :as opts}]
  (ring/ring-handler
    router
    (ring/routes
      (ring/redirect-trailing-slash-handler)
      spa-handler
      (ring/create-default-handler
        {:not-found
         (constantly (-> {:status 404, :body "Page not found"}
                       (http-response/content-type "text/html")))
         :method-not-allowed
         (constantly (-> {:status 405, :body "Not allowed"}
                       (http-response/content-type "text/html")))
         :not-acceptable
         (constantly (-> {:status 406, :body "Not acceptable"}
                       (http-response/content-type "text/html")))}))
    {:middleware [(middleware/wrap-base opts)
                  (middleware/ctx-middleware
                    (fn [request]
                      (assoc request
                        :ezcl/ctx (select-keys opts [:postgres/datasource]))))]}))

(defmethod ig/init-key :handler/ring
  [_ opts]
  (ring-handler opts))

(defmethod ig/init-key :router/routes
  [_ {:keys [routes]}]
  (apply conj [] routes))

(defmethod ig/init-key :router/core
  [_ {:keys [routes] :as opts}]
  (ring/router ["" opts routes]
    {:conflicts (fn [conflicts]
                  (when (some (fn [[_conflicting-path conflict-set]]
                                (not
                                  (and
                                    (= 1 (count conflict-set))
                                    (:default-handler? (second (first conflict-set)))))) conflicts)
                    ((:conflicts (reitit/default-router-options)) conflicts)))}))
