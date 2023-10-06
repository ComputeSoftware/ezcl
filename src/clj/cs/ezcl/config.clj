(ns cs.ezcl.config
  (:require
    [aero.core :as aero]
    [clojure.java.io :as io]
    [integrant.core :as ig]
    [kwill.logger :as log]))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'ig/refset
  [_ _ value]
  (ig/refset value))

(defn read-config
  [filename options]
  (log/info {:msg "Reading config" :filename filename})
  (aero/read-config (io/resource filename) options))

(defmethod ig/init-key :system/env [_ env] env)

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (aero/read-config (io/resource system-filename) options))
