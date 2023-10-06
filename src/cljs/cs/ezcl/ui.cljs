(ns cs.ezcl.ui
  (:require
    [cs.ezcl.ui.app-nav :as app-nav]
    [cs.ezcl.ui.instance-group :as instance-group]
    [cs.ezcl.ui.routing :as routing]
    [cs.ezcl.ui.zone :as zone]
    ["@headlessui/react" :as h.ui]
    ["@heroicons/react/24/outline" :as icons]
    ["react" :as react]
    [kwill.uix-state :as ss]
    ["@tanstack/react-query" :as t.rq]
    ["@clerk/clerk-react" :as clerk]
    [lambdaisland.glogi.console :as glogi-console]
    [uix.core :refer [$ defui]]
    [uix.dom]))

(defui app []
  (let [{:keys [disp db]} (ss/use-app-db)
        _ (routing/use-routing :disp disp)]
    ($ :div
      ($ app-nav/sidebar {})
      ($ :div {:class "lg:pl-72"}
        ($ app-nav/top-nav {})
        ($ :main {:class "py-10"}
          (case (-> db :ui/route :route/name)
            :page/root "Dashboard"
            :page/instance-group-list ($ instance-group/ig)
            :page/instance-group ($ instance-group/ig-details {:instance_group/id (-> db :ui/route :route/path-params :id)})
            :page/instance-group-create ($ instance-group/create-ig)
            :page/zone-list ($ zone/z-list)
            :page/zone-create ($ zone/create-z)
            :page/settings "Settings"
            "404"))))))

(def query-client (t.rq/QueryClient.))

(goog-define clerk-publishable-key false)

(defn root-comp
  []
  ($ ss/AppDbProvider
    ($ clerk/ClerkProvider {:publishableKey clerk-publishable-key}
      ($ clerk/SignedIn
        ($ t.rq/QueryClientProvider {:client query-client}
          ($ app)))
      ($ clerk/SignedOut
        ($ clerk/RedirectToSignIn)))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ root-comp) root))

(defn ^:export init []
  (glogi-console/install!)
  (render))
