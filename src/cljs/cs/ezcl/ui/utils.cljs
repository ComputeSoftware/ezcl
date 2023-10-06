(ns cs.ezcl.ui.utils
  (:require
    [cljs-bean.core :as bean]
    [cljs-http.client :as http]
    [clojure.string :as str]
    [cognitect.transit :as transit]
    ["@tanstack/react-query" :as t.rq]))

(def default-writer (transit/writer :json))

(defn write
  [x]
  (transit/write default-writer x))

(defn ->coll
  [x]
  (if (sequential? x) x (vector x)))

(defn form-map
  [e]
  (reduce
    (fn [acc [k raw-v]]
      (if (str/blank? raw-v)
        acc
        (let [k-data (transit/read (transit/reader :json) k)
              {:keys [key as]} (if (map? k-data)
                                 k-data
                                 {:key k-data})
              parse-fn (case as
                         :int parse-long
                         identity)
              v (parse-fn raw-v)]
          (update-in acc (->coll key)
            (fn [cur-v]
              (if cur-v (conj [cur-v] v) v))))))
    {} (.entries (js/FormData. e))))

(defn request
  [request-map]
  (http/request
    (-> request-map
      (update :headers #(merge {"accept" "application/transit+json"} %))
      (cond->
        (:transit-params request-map)
        (->
          (assoc-in [:headers "content-type"] "application/transit+json"))))))

(defn use-query
  [argm]
  (bean/->clj (t.rq/useQuery (bean/->js argm))))

(defn use-mutation
  [argm]
  (bean/->clj (t.rq/useMutation (bean/->js argm))))

(defn invalidate-queries
  [query-client argm]
  (.invalidateQueries ^js query-client (bean/->js argm)))
