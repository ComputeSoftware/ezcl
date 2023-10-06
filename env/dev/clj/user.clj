(ns user
  (:require
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :as repl]
    [cs.ezcl.core]
    [expound.alpha :as expound]
    [integrant.core :as ig]
    [integrant.repl :as ig.r]
    [sc.api]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn dev-prep!
  []
  (integrant.repl/set-prep! (fn []
                              (-> (cs.ezcl.config/system-config {:profile :dev})
                                (ig/prep)))))

(dev-prep!)

(repl/set-refresh-dirs "src/clj")

(def refresh repl/refresh)

(comment
  (refresh)
  (ig.r/go)
  (ig.r/reset))
