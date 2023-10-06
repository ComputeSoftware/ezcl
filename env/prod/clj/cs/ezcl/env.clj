(ns cs.ezcl.env
  (:require [kwill.logger :as log]))

(def defaults
  {:init       (fn []
                 (log/info {:msg "ezcl starting"}))
   :start      (fn []
                 (log/info {:msg "ezcl started"}))
   :stop       (fn []
                 (log/info {:msg "ezcl stopped"}))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
