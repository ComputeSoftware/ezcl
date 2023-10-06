(ns cs.ezcl.core
  (:require
    [cs.ezcl.config :as config]
    [cs.ezcl.env :as env]
    [cs.ezcl.handler]
    [integrant.core :as ig]
    [kit.edge.server.undertow]
    [kwill.logger :as log])
  (:gen-class))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:msg (str "Uncaught exception on " (.getName thread)) :throwable ex}))))

(defonce *system (atom nil))

(defn stop-app []
  ((or (:stop env/defaults) (fn [])))
  (some-> (deref *system) (ig/halt!))
  (shutdown-agents))

(defn start-app [& [params]]
  ((or (:start params) (:start env/defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts env/defaults) {}))
    (ig/prep)
    (ig/init)
    (reset! *system))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main
  [& _]
  (start-app))
