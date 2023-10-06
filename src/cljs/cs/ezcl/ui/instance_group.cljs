(ns cs.ezcl.ui.instance-group
  (:require
    [cljc.java-time.instant :as instant]
    [cljc.java-time.temporal.chrono-unit :as chrono-unit]
    [cs.ezcl.ui.components :as components]
    [cs.ezcl.ui.routing :as routing]
    [cs.ezcl.ui.utils :as utils]
    [kwill.uix-state :as ss]
    [lambdaisland.glogi :as log]
    [uix.core :as uix :refer [$ defui]]
    ["@heroicons/react/24/outline" :as icons]
    ["@tanstack/react-query" :as t.rq]
    ["@headlessui/react" :as h.ui]))

(defui ig-name
  [{zone-name           :zone/name
    instance-group-name :instance_group/name
    size                :size}]
  ($ :h2 {:class (str
                   "min-w-0 font-semibold text-black"
                   (case size
                     :large
                     "text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:text-3xl sm:tracking-tight"
                     "text-sm leading-6"))}
    ($ :div {:class "flex gap-x-2"}
      ($ :span {:class "truncate"} zone-name)
      ($ :span {:class "text-gray-400"} "/")
      ($ :span {:class "whitespace-nowrap"} instance-group-name))))

(defn delete-mutation
  [query-client]
  (utils/use-mutation
    {:mutationFn (fn [instance-group]
                   (utils/request {:method         :post
                                   :url            "/api/DeleteInstanceGroup"
                                   :transit-params instance-group}))
     :onSuccess  (fn [data]
                   (utils/invalidate-queries query-client {:queryKey ["ListInstanceGroups"]}))}))

(defui ig-list-one
  [{:keys [instance-group]}]
  (let [{instance-group-name  :instance_group/name
         :instance_group/keys [id zone]} instance-group
        query-client (t.rq/useQueryClient)
        status-resp (utils/use-query {:queryKey        ["DescribeInstanceGroupStatus" id]
                                      :queryFn         (fn []
                                                         (utils/request {:method         :post
                                                                         :url            "/api/DescribeInstanceGroupStatus"
                                                                         :transit-params {:instance_group/id id}}))
                                      :refetchInterval (fn [response]
                                                         (when-not (contains? components/deployment-end-states (-> response :body :data :deployment/status))
                                                           1000))})
        delete-mutation (delete-mutation query-client)
        retry-mutation (utils/use-mutation
                         {:mutationFn (fn [instance-group]
                                        (utils/request {:method         :post
                                                        :url            "/api/RetryInstanceGroup"
                                                        :transit-params instance-group}))
                          :onSuccess  (fn [data]
                                        (utils/invalidate-queries query-client {:queryKey ["ListInstanceGroups"]}))})
        deployment (cond-> (-> status-resp :data :body :data)
                     (:isLoading delete-mutation)
                     (assoc :deployment/status :deployment.status/deleting))
        href (routing/href {:route/name        :page/instance-group
                            :route/path-params {:id id :tab (name :page.instance-group.tab/overview)}})]
    ($ components/stacked-list-item
      {:title            ($ :div {:class "flex items-start gap-x-3"}
                           ($ ig-name {:zone/name                     (:zone/name zone)
                                                 :instance_group/name instance-group-name})
                           ($ components/deployment-status {:deployment deployment}))
       :right-cta-button ($ components/buton {:kind :secondary :as :a :href href} "View Details")
       :right-menu       {:items [{:label    "Retry Deployment"
                                   :on-click #((:mutate retry-mutation) {:instance_group/id id})}
                                  {:label    "Delete"
                                   :on-click #((:mutate delete-mutation) {:instance_group/id id})}]}})))

(defui ig-list
  []
  (let [{:keys [data]} (utils/use-query {:queryKey ["ListInstanceGroups"]
                                         :queryFn  (fn []
                                                     (utils/request {:method :post
                                                                     :url    "/api/ListInstanceGroups"}))})
        instance-groups (-> data :body :data)]
    ($ components/stacked-list
      (for [{:keys [:instance_group/id] :as instance-group} instance-groups]
        ($ ig-list-one {:key                      id
                                  :instance-group instance-group})))))

(defui ig
  []
  ($ :div {:class "px-4 sm:px-6 lg:px-8"}
    ($ components/list-header {:title              "Instance Groups"
                              :action-button-right {:text     "Create Instance Group"
                                                    :on-click #(routing/navigate! {:route/name :page/instance-group-create})}})
    ($ ig-list)))

(defui endpoint-row
  [{:keys [on-remove-row default-endpoint]}]
  ($ :div {:class "flex flex-row space-x-6"}
    ($ :input {:type          "hidden"
               :hidden        true
               :default-value (:endpoint/id default-endpoint)
               :id            (utils/write {:key [:instance_group/endpoints :endpoint/id]})})
    ($ :div {:class "flex-1"}
      ($ components/form-input
        {:label         "Endpoint Name"
         :id            (utils/write [:instance_group/endpoints :endpoint/name])
         :default-value (:endpoint/name default-endpoint)}))
    ($ :div {:class "flex-1"}
      ($ components/form-input
        {:label         "From Port"
         :type          "number"
         :default-value (:endpoint/from_port default-endpoint)
         :id            (utils/write {:key [:instance_group/endpoints :endpoint/from_port]
                                      :as  :int})}))
    ($ :div {:class "flex-1"}
      ($ components/form-input
        {:label         "To Port"
         :type          "number"
         :default-value (:endpoint/to_port default-endpoint)
         :id            (utils/write {:key [:instance_group/endpoints :endpoint/to_port]
                                      :as  :int})}))
    ($ :div {:class "flex items-center"}
      ($ icons/XMarkIcon {:aria-hidden "true"
                          :class       "h-6 w-6 shrink-0 text-indigo-200 group-hover:text-white cursor-pointer"
                          :on-click    #(on-remove-row)}))))

(defui create-btns
  [{:keys [on-cancel
           on-cancel-href
           submitting?
           submit-noun
           submit-verb
           submitting-verb]}]
  ($ :div {:class "mt-6 flex items-center justify-end gap-x-6"}
    ($ :a {:type     "button"
           :class    "text-sm font-semibold leading-6 text-gray-900"
           :on-click on-cancel
           :href     on-cancel-href}
      "Cancel")
    ($ components/buton
      {:type     "submit"
       :disabled submitting?}
      (if submitting?
        (str submitting-verb " " submit-noun "...")
        (str submit-verb " " submit-noun)))))

(defui the-form
  [{:keys [on-submit
           form-error
           default-instance-group]
    :as   argm}]
  (let [[endpoints set-endpoints] (uix/use-state (mapv (fn [e] {:key      (:endpoint/id e)
                                                                :endpoint e}) (:instance_group/endpoints default-instance-group)))
        add-endpoint (fn [] (set-endpoints (conj endpoints {:key (random-uuid)})))
        remove-endpoint (fn [idx] (set-endpoints (into (subvec endpoints 0 idx) (subvec endpoints (inc idx)))))]
    ($ :form {:on-submit (fn [event]
                           (.preventDefault event)
                           (let [form (utils/form-map (.-target event))
                                 instance-group {:instance_group/name            (:instance_group/name form)
                                                 :instance_group/container       (:instance_group/container form)
                                                 :instance_group/run_flags       (:instance_group/run_flags form)
                                                 :instance_group/github_username (:instance_group/github_username form)
                                                 :instance_group/zone_id         (:instance_group/zone_id form)
                                                 :instance_group/endpoints       (utils/->coll (:instance_group/endpoints form))}]
                             (on-submit instance-group)))}
      ($ :div {:class "space-y-12"}
        ($ :div {:class "border-b border-gray-900/10 pb-12"}
          ($ :h2 {:class "text-base font-semibold leading-7 text-gray-900"} "Name")

          (when form-error
            ($ :div {:class "mt-6"}
              ($ components/alert
                {:color :red
                 :title form-error})))

          ($ :div {:class "mt-6 grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-6"}
            ($ :div {:class "sm:col-span-3"}
              ($ components/form-input {:label        "Group name"
                                       :id            (utils/write :instance_group/name)
                                       :default-value (:instance_group/name default-instance-group)}))
            ($ :div {:class "sm:col-span-3 sm:col-start-1"}
              ($ components/zone-selector {:default-value (-> default-instance-group :instance_group/zone :zone/id)
                                          :input-id       (utils/write :instance_group/zone_id)}))))

        ($ :div {:class "border-b border-gray-900/10 pb-12"}
          ($ :h2 {:class "text-base font-semibold leading-7 text-gray-900"} "Start Config")

          ($ :div {:class "mt-6 grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-6"}
            ($ :div {:class "sm:col-start-1 sm:col-span-3"}
              ($ components/form-input {:label        "Container"
                                       :id            (utils/write :instance_group/container)
                                       :default-value (:instance_group/container default-instance-group)}))

            ($ :div {:class "sm:col-span-3"}
              ($ components/form-input {:label        "Run flags"
                                       :id            (utils/write :instance_group/run_flags)
                                       :default-value (:instance_group/run_flags default-instance-group)}))))

        ($ :div {:class "border-b border-gray-900/10 pb-12"}
          ($ :h2 {:class "text-base font-semibold leading-7 text-gray-900"} "Ports")

          ($ :div {:class "mt-6 flex flex-col space-y-4"}
            (for [[idx {:keys [key endpoint]}] (map-indexed vector endpoints)]
              ($ endpoint-row {:key             key
                              :default-endpoint endpoint
                              :on-remove-row    #(remove-endpoint idx)})))

          (if (zero? (count endpoints))
            ($ :button
              {:type     "button",
               :class    "relative block w-full rounded-lg border-2 border-dashed border-gray-300 p-12 text-center hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
               :on-click #(add-endpoint)}
              ($ icons/GlobeAltIcon {:class "mx-auto h-12 w-12 text-gray-400"})
              ($ :span {:class "mt-2 block text-sm font-semibold text-gray-900"} "Add Public Endpoint"))
            ($ components/buton {:kind      :tertiary
                                  :class    "mt-5"
                                  :on-click #(add-endpoint)}
              ($ :span {:aria-hidden true} "+ ") "Add Port")))

        ($ :div {:class "border-b border-gray-900/10 pb-12"}
          ($ :h2 {:class "text-base font-semibold leading-7 text-gray-900"} "SSH")
          ($ :div {:class "mt-6 flex flex-col space-y-4"}
            ($ :div {:class "sm:col-span-3 sm:col-start-1"}
              ($ components/form-input {:label        "GitHub username"
                                       :id            (utils/write :instance_group/github_username)
                                       :default-value (:instance_group/github_username default-instance-group)})))))

      ($ create-btns (assoc argm :submit-noun "Instance Group")))))

(defui create-ig
  []
  (let [mutation (utils/use-mutation
                   {:mutationFn (fn [instance-group]
                                  (utils/request {:method         :post
                                                  :url            "/api/CreateInstanceGroup"
                                                  :transit-params instance-group}))
                    :onSuccess  (fn [data]
                                  (log/info :CreateInstanceGroup data)
                                  (routing/navigate! {:route/name :page/instance-group-list}))
                    :onError    #(js/console.log "error" %)})
        form-base {:on-submit                     #((:mutate mutation) %)
                   :form-error                    (when (:isError mutation) "Create failed :(")
                   :submitting?                   (:isLoading mutation)
                   :submit-button-submitting-text "Creating..."}]
    ($ :div {:class "mx-auto max-w-2xl"}
      ($ the-form
        (assoc form-base
          :submit-verb "Create"
          :submitting-verb "Creating")))))


(defui ig-instance-list
  [{:keys [instance-group]}]
  (let [{:instance_group/keys [id]} instance-group
        instance-group-response (utils/use-query {:queryKey ["DescribeInstanceGroupInstances" id]
                                                  :queryFn  (fn []
                                                              (utils/request {:method         :post
                                                                              :url            "/api/DescribeInstanceGroupInstances"
                                                                              :transit-params {:instance_group/id id}}))})
        instances (-> instance-group-response :data :body :data :instance_group/instances)]
    ($ :div
      ($ :ul {:class "list-disc list-inside"}
        (for [{:instance/keys [instance_id public_ip_address]} instances]
          ($ :li {:key instance_id}
            ($ :span public_ip_address)))))))

(defui overview
  [{:keys [instance-group]}]
  ($ :div {:class ["flex" "flex-col" "space-y-6"]}
    ($ :div
      ($ :h4 {:class "text-2xl font-bold dark:text-white"} "Endpoints")
      ($ :ul {:class "list-disc list-inside"}
        (for [{:endpoint/keys [id from_port]} (:instance_group/endpoints instance-group)
              :let [host (str id ".ezcl.computesoftware.com")]]
          ($ :li {:key id}
            ($ :span host ":" from_port)))))
    ($ :div
      ($ :h4 {:class "text-2xl font-bold dark:text-white"} "Instances")
      ($ ig-instance-list {:instance-group instance-group}))))

(defui logs
  [{:keys [instance-group]}]
  (let [{:instance_group/keys [id]} instance-group
        [date-params] (uix/use-state
                        (let [stop-date (instant/now)]
                          {:start-date (-> stop-date (instant/minus 5 chrono-unit/minutes) instant/to-epoch-milli (js/Date.))
                           :stop-date  (-> stop-date instant/to-epoch-milli (js/Date.))}))
        {:keys [start-date stop-date]} date-params
        logs-response (utils/use-query {:queryKey ["GetInstanceGroupLogs" id]
                                        :queryFn  (fn []
                                                    (utils/request {:method         :post
                                                                    :url            "/api/GetInstanceGroupLogs"
                                                                    :transit-params (merge {:instance_group/id id} date-params)}))})
        log-events (-> logs-response :data :body :data)]
    ($ :div
      ($ :span
        "Date range:  "
        ($ components/date-comp {:date start-date})
        "-"
        ($ components/date-comp {:date stop-date}))
      ($ :div {:class ["text-sm" "bg-slate-100" "p-4" "overflow-x-auto"]}
        (if (:isLoading logs-response)
          ($ :span "Loading...")
          (for [{:log_event/keys [id message timestamp]} log-events]
            ($ :div {:key   id
                     :class ["flex" "space-x-2"]}
              ($ components/date-comp {:date timestamp})
              ($ :pre message))))))))

(defui settings
  [{:keys [instance-group route-map]}]
  (let [query-client (t.rq/useQueryClient)
        update-mutation (utils/use-mutation
                          {:mutationFn (fn [instance-group]
                                         (utils/request {:method         :post
                                                         :url            "/api/UpdateInstanceGroup"
                                                         :transit-params instance-group}))
                           :onSuccess  #(utils/invalidate-queries query-client {:queryKey ["ListInstanceGroups"]})})]
    ($ the-form {:default-instance-group instance-group
             :submit-verb                "Update"
             :submitting-verb            "Updating"
             :on-submit                  #((:mutate update-mutation) %)
             :submitting?                (:isLoading update-mutation)
             :on-cancel-href             (routing/href (assoc-in route-map
                                                     [:route/path-params :tab]
                                                     (name :page.instance-group.tab/overview)))})))

(defui ig-details
  [{:instance_group/keys [id]}]
  (let [instance-group-response (utils/use-query {:queryKey ["DescribeInstanceGroup" id]
                                                  :queryFn  (fn []
                                                              (utils/request {:method         :post
                                                                              :url            "/api/DescribeInstanceGroup"
                                                                              :transit-params {:instance_group/id id}}))})
        query-client (t.rq/useQueryClient)
        delete-mutation (delete-mutation query-client)
        route-map (ss/sub :sub.route/route)
        selected-tab (some->> route-map :route/path-params :tab
                       (keyword (name :page.instance-group.tab)))
        instance-group (-> instance-group-response :data :body :data)]
    ($ :div {:class "px-4 sm:px-6 lg:px-8"}
      (if (:isLoading instance-group-response)
        "Loading..."
        ($ :<>
          ($ :div {:class "lg:flex lg:items-center lg:justify-between mb-10"}
            ($ :div {:class "min-w-0 flex-1"}
              ($ ig-name
                {:zone/name           (-> instance-group :instance_group/zone :zone/name)
                 :instance_group/name (:instance_group/name instance-group)
                 :size                :large}))

            ($ :div {:class "mt-5 flex lg:ml-4 lg:mt-0 space-x-3"}
              ($ :span {:class "hidden sm:block"}
                ($ components/buton {:kind          :secondary
                                      :leading-icon icons/TrashIcon
                                      :size         4
                                      :on-click     (fn []
                                                      (if (js/confirm "This is a permanent action. Are you sure you want to delete the instance group?")
                                                        (:mutate delete-mutation)))}
                  "Delete"))))
          ($ components/tabs
            {:selected-id     selected-tab
             :container-class "mb-8"
             :tabs            [{:label "Overview"
                                :id    :page.instance-group.tab/overview
                                :href  (routing/href (assoc-in route-map
                                                       [:route/path-params :tab]
                                                       (name :page.instance-group.tab/overview)))}
                               {:label "Logs"
                                :id    :page.instance-group.tab/logs
                                :href  (routing/href (assoc-in route-map
                                                       [:route/path-params :tab]
                                                       (name :page.instance-group.tab/logs)))}
                               {:label "Settings"
                                :id    :page.instance-group.tab/settings
                                :href  (routing/href (assoc-in route-map
                                                       [:route/path-params :tab]
                                                       (name :page.instance-group.tab/settings)))}]})
          (case selected-tab
            :page.instance-group.tab/overview
            ($ overview {:instance-group instance-group})
            :page.instance-group.tab/logs
            ($ logs {:instance-group instance-group})
            :page.instance-group.tab/settings
            ($ settings {:instance-group instance-group
                         :route-map      route-map})
            "unknown tab"))))))
