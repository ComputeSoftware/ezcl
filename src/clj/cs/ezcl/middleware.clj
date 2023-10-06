(ns cs.ezcl.middleware
  (:require
    [buddy.core.keys :as keys]
    [buddy.sign.jwt :as jwt]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [cognitect.transit :as transit]
    [hato.client :as http]
    [integrant.core :as ig]
    [kwill.anomkit :as ak]
    [kwill.anomkit :as ak]
    [luminus-transit.time :as time]
    [muuntaja.core :as m]
    [reitit.ring.middleware.exception :as exception]
    [ring.middleware.defaults :as defaults])
  (:import (java.time Instant ZoneId)))

(def time-serialization-handlers
  (update time/time-serialization-handlers :handlers assoc
    java.time.Instant (transit/write-handler
                        (constantly "Instant")
                        (fn [^Instant instant]
                          (.format (.withZone time/iso-zoned-date-time (ZoneId/of "UTC"))
                            instant)))))

(def time-deserialization-handlers
  (update time/time-deserialization-handlers :handlers assoc
    "Instant" (transit/read-handler #(Instant/parse %))))

(def instance
  (m/create
    (-> m/default-options
      (update-in
        [:formats "application/transit+json" :decoder-opts]
        (partial merge time-deserialization-handlers))
      (update-in
        [:formats "application/transit+json" :encoder-opts]
        (partial merge time-serialization-handlers)))))

(def wrap-exception (exception/create-exception-middleware))

(defn wrap-base
  [{:keys [site-defaults-config]}]
  (fn [handler]
    (defaults/wrap-defaults handler site-defaults-config)))

(defn wrap-ctx
  ([handler]
   (wrap-ctx handler {}))
  ([handler updatef]
   (fn
     ([request]
      (handler (updatef request)))
     ([request respond raise]
      (handler (updatef request) respond raise)))))

(defn ctx-middleware
  [updatef]
  {:name ::ctx-middleware
   :wrap #(wrap-ctx % updatef)})

(defn fetch-jwks
  [& {:keys [jwks-url kid]}]
  (let [jwks (-> (http/get jwks-url)
               :body
               (json/read-str :key-fn keyword))
        jwk (some (fn [jwk] (when (= kid (:kid jwk)) jwk)) (:keys jwks))]
    (when-not jwk
      (ak/not-found! (format "No JWK found for specified kid '%s'" kid)
        {:kid kid :jwks-data jwks}))
    {:jwks-data jwks
     :kid       kid
     :jwk       (some (fn [jwk] (when (= kid (:kid jwk)) jwk)) (:keys jwks))
     :alg       (-> jwk :alg str/lower-case keyword)}))

(defmethod ig/init-key :authentication/jwks-data
  [_ fetch-jwks-argm]
  (fetch-jwks fetch-jwks-argm))

(def wrap-authentication
  {:name    ::wrap-authentication
   :compile (fn [route-data _opts]
              (when-let [_ (:authenticated route-data)]
                (fn [handler {:keys [jwk alg]}]
                  (let [pub-key (keys/jwk->public-key jwk)]
                    (fn [request]
                      (let [{:keys [cookies]} request]
                        (if-let [session-data (some-> (get cookies "__session") :value
                                                (jwt/unsign pub-key {:alg alg}))]
                          (handler (assoc request :ezcl/session session-data))
                          (ak/forbidden! "Forbidden"
                            {:type   :system.exception/unauthorized
                             :status 401}))))))))})
