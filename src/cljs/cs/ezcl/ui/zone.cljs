(ns cs.ezcl.ui.zone
  (:require
    [cs.ezcl.ui.components :as components]
    [cs.ezcl.ui.routing :as routing]
    [cs.ezcl.ui.utils :as utils]
    [uix.core :refer [$ defui]]
    ["@heroicons/react/24/outline" :as icons]
    ["@tanstack/react-query" :as t.rq]
    ["@headlessui/react" :as h.ui]))

(defui list-item
  [{:keys [zone]}]
  (let [{:keys [:zone/id :zone/name]} zone
        query-client (t.rq/useQueryClient)
        delete-mutation (utils/use-mutation
                          {:mutationFn (fn [zone]
                                         (utils/request {:method         :post
                                                         :url            "/api/DeleteZone"
                                                         :transit-params zone}))
                           :onSuccess  #(utils/invalidate-queries query-client {:queryKey ["ListZones"]})})
        zone-status-response (utils/use-query {:queryKey        ["DescribeZoneStatus" id]
                                               :queryFn         (fn []
                                                                  (utils/request {:method         :post
                                                                                  :url            "/api/DescribeZoneStatus"
                                                                                  :transit-params {:zone/id id}}))
                                               :refetchInterval (fn [response]
                                                                  (when (not= :deployment.status/succeeded (-> response :body :data :deployment/status))
                                                                    1000))})
        zone-status (-> zone-status-response :data :body :data)]
    ($ components/stacked-list-item
      {:title      ($ :div {:class "flex items-start gap-x-3"}
                     ($ :h2 {:class "min-w-0 text-sm font-semibold leading-6 text-black"}
                       (:zone/name zone))
                     ($ components/deployment-status {:deployment zone-status}))
       :sub-titles [($ :p {:class "whitespace-nowrap"}
                      "Start Date "
                      ($ components/date-comp {:date (:zone/creation_date zone)}))]
       :right-menu {:items [{:label    "Delete"
                             :on-click #((:mutate delete-mutation) {:zone/id id})}]}})))

(defui z-list
  []
  (let [{:keys [data]} (utils/use-query {:queryKey ["ListZones"]
                                         :queryFn  (fn []
                                                     (utils/request {:method :post
                                                                     :url    "/api/ListZones"}))})
        zones (-> data :body :data)]
    ($ :div {:class "px-4 sm:px-6 lg:px-8"}
      ($ components/list-header {:title              "zones"
                                :action-button-right {:text "Create zone"
                                                      :href (routing/href {:route/name :page/zone-create})}})

      ($ components/stacked-list
        (for [{:keys [:zone/id] :as zone} zones]
          ($ list-item {:key id :zone zone}))))))

(defui create-z
  []
  (let [mutation (utils/use-mutation
                   {:mutationFn (fn [zone]
                                  (utils/request {:method         :post
                                                  :url            "/api/CreateZone"
                                                  :transit-params zone}))
                    :onSuccess  #(routing/navigate! {:route/name :page/zone-list})})]
    ($ :div {:class "mx-auto max-w-2xl"}
      ($ :form {:on-submit (fn [event]
                             (.preventDefault event)
                             (let [data (js/FormData. (.-target event))]
                               ((:mutate mutation) {:zone/name (.get data "zone-name")})))}
        ($ :div {:class "space-y-12"}
          ($ :div {:class "border-b border-gray-900/10 pb-12"}
            ($ :h2 {:class "text-base font-semibold leading-7 text-gray-900"} "Create zone")
            ($ :div {:class "mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6"}
              ($ :div {:class "sm:col-span-3"}
                ($ :label {:htmlFor "zone-name"
                           :class   "block text-sm font-medium leading-6 text-gray-900"}
                  "zone name")
                ($ :div {:class "mt-2"}
                  ($ :input
                    {:type  "text"
                     :name  "zone-name"
                     :id    "zone-name"
                     :class "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"}))))))
        ($ :div {:class "mt-6 flex items-center justify-end gap-x-6"}
          ($ :a {:type  "button"
                 :class "text-sm font-semibold leading-6 text-gray-900"}
            "Cancel")
          ($ components/buton
            {:type     "submit"
             :disabled (:isLoading mutation)}
            (if (:isLoading mutation)
              "Creating..."
              "Create zone")))))))
