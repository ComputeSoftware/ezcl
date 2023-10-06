(ns cs.ezcl.ui.routing
  (:require
    [goog.object :as gobj]
    [kwill.uix-state :as ss]
    [reitit.core :as r]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.history :as rfh]
    [uix.core :as uix]))

(def routes
  [[""
    ["/" :page/root]

    ["/instance-groups"
     ["" :page/instance-group-list]
     ["/create" :page/instance-group-create]]

    ["/instance-group/:id/:tab" :page/instance-group]

    ["/zones"
     ["" :page/zone-list]
     ["/create" :page/zone-create]]

    ["/settings" :page/settings]]])

(def router (r/router routes))

(defn href
  [route-map]
  (-> router
    (r/match-by-name (:route/name route-map) (:route/path-params route-map))
    :path))

(defn navigate!
  [route-map]
  (rfe/push-state (:route/name route-map) (:route/path-params route-map) (:route/query-params route-map)))

(ss/reg-event! :evt.routing/navigate
  {}
  (fn [{:keys [db]} {:keys [match]}]
    (let [{:keys [data parameters]} match
          {:keys [path query fragment]} parameters]
      {:db (assoc db :ui/route (cond-> {:route/name (:name data)}
                                 path (assoc :route/path-params path)
                                 query (assoc :route/query-params query)))})))

(ss/reg-sub! :sub.route/route {}
  (fn [{:keys [db]} _]
    (:ui/route db)))

(ss/reg-sub! :sub.route/name {}
  (fn [{:keys [db]} _]
    (-> db :ui/route :route/name)))

(ss/reg-sub! :sub.route/path-params {}
  (fn [{:keys [db]} _]
    (-> db :ui/route :route/path-params)))

(defn- on-navigate-fn
  [disp route-match]
  (disp {:id    :evt.routing/navigate
         :match route-match}))

(defn init!
  [& {:keys [disp]}]
  (rfe/start!
    router
    #(on-navigate-fn disp %)
    {:use-fragment         false
     :ignore-anchor-click? (fn [router e el uri]
                             (and (rfh/ignore-anchor-click? router e el uri)
                               (not= "false" (gobj/get (.-dataset el) "reititHandleClick"))))}))

(defn use-routing
  [& {:keys [disp] :as argm}]
  (uix/use-effect (fn [] (init! :disp disp)) [disp]))
