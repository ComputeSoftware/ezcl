(ns cs.ezcl.ui.components
  (:require
    [cljc.java-time.format.date-time-formatter :as date-time-formatter]
    [cljc.java-time.instant :as instant]
    [cljc.java-time.zone-id :as zone-id]
    [clojure.string :as str]
    [cs.ezcl.ui.utils :as utils]
    [uix.core :as uix :refer [$ defui]]
    ["@headlessui/react" :as h.ui]
    ["@heroicons/react/24/outline" :as icons]
    ["react" :as react]
    [uix.dom]
    ["@tanstack/react-query" :as t.rq]))

(defui buton
  [{:keys [as kind size disabled leading-icon trailing-icon]
    :or   {as   :button
           kind :primary
           size 3}
    :as   opts}]
  ($ as
    (merge {:type  "button"
            :class [(case kind
                      :primary
                      "bg-indigo-600 font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                      :secondary
                      "bg-white font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                      :tertiary
                      "text-sm font-semibold leading-6 text-indigo-600 hover:text-indigo-500"
                      "")
                    (case size
                      1 "rounded px-2 py-1 text-xs"
                      2 "rounded px-2 py-1 text-sm"
                      3 "rounded-md px-2.5 py-1.5 text-sm"
                      4 "rounded-md px-3 py-2 text-sm"
                      5 "rounded-md px-3.5 py-2.5 text-sm"
                      6 "rounded-md px-3.5 py-2.5 text-sm"
                      "")
                    (when disabled
                      "disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-500")
                    (when (= :a as) "cursor-pointer")
                    (when (or trailing-icon leading-icon) "inline-flex items-center gap-x-1.5")
                    (:class opts)]}
      (dissoc opts :class :children :leading-icon :trailing-icon))
    (when leading-icon
      ($ leading-icon {:class "-ml-0.5 h-5 w-5" :aria-hidden true}))
    (:children opts)
    (when trailing-icon
      ($ trailing-icon {:class "-ml-0.5 h-5 w-5" :aria-hidden true}))))

(defui combobox
  [{:keys [options input-name input-label nullable default-value on-change]}]
  (let [[search set-search] (uix/use-state "")
        id->option (into {} (map (juxt :id identity) options))
        filtered-options (cond->> options
                           (not (str/blank? search))
                           (filter (fn [{:keys [label]}]
                                     (str/includes? (str/lower-case (str label)) (str/lower-case search)))))
        [selected set-selected] (uix/use-state default-value)
        on-change (fn [v] (set-selected v) (some-> on-change (v)))]
    ($ h.ui/Combobox {:as       "div"
                      :value    selected
                      :onChange on-change
                      :name     input-name
                      :nullable nullable}
      ($ h.ui/Combobox.Label {:class "block text-sm font-medium leading-6 text-gray-900"} input-label)
      ($ :div {:class "relative mt-2"}
        ($ h.ui/Combobox.Input
          {:class        "w-full rounded-md border-0 bg-white py-1.5 pl-3 pr-10 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
           :on-change    (fn [event] (set-search (.. event -target -value)))
           :displayValue (fn [selected-value] (-> selected-value id->option :label))})

        ($ h.ui/Combobox.Button {:class "absolute inset-y-0 right-0 flex items-center rounded-r-md px-2 focus:outline-none"}
          ($ icons/ChevronUpDownIcon {:aria-hidden "true", :class "h-5 w-5 text-gray-400"}))

        ($ h.ui/Combobox.Options {:class "absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm"}
          (for [{:keys [id label]} filtered-options]
            ($ h.ui/Combobox.Option {:key   id
                                     :value id
                                     :class (fn [js-map]
                                              (str "relative cursor-default select-none py-2 pl-3 pr-9 "
                                                (if (.-active js-map)
                                                  "bg-indigo-600 text-white"
                                                  "text-gray-900")))}
              (fn [js-map]
                ($ :<>
                  ($ :span {:class (str "block truncate " (when (.-selected js-map) "font-semibold"))}
                    label)
                  (when (.-selected js-map)
                    ($ :span {:class (str "absolute inset-y-0 right-0 flex items-center pr-4 "
                                       (if (.-active js-map)
                                         "text-white"
                                         "text-indigo-600"))}
                      ($ icons/CheckIcon {:class       "h-5 w-5"
                                          :aria-hidden true}))))))))))))

(def alert-color->classes
  {:red   {:icon       icons/XCircleIcon
           :background "bg-red-50"
           :icon-color "text-red-400"
           :text-color "text-red-800"}
   :green {:icon       icons/CheckCircleIcon
           :background "bg-green-50"
           :icon-color "text-green-400"
           :text-color "text-green-800"}})

(defui alert
  [{:keys [color title]}]
  (let [{:keys [icon background icon-color text-color]} (alert-color->classes color)]
    ($ :div {:class (str "rounded-md p-4 " background)}
      ($ :div {:class "flex"}
        ($ :div {:class "flex-shrink-0"}
          ($ icon {:aria-hidden "true"
                   :class       (str "h-5 w-5 " icon-color)}))
        ($ :div {:class "ml-3"}
          ($ :h3 {:class (str "text-sm font-medium " text-color)} title))))))

(def status->class
  {:green  "text-green-700 bg-green-50 ring-green-600/20"
   :gray   "text-gray-600 bg-gray-50 ring-gray-500/10"
   :yellow "text-yellow-800 bg-yellow-50 ring-yellow-600/20"})

(def status-label-color->class
  {:green  "text-green-700 bg-green-50 ring-green-600/20"
   :gray   "text-gray-600 bg-gray-50 ring-gray-500/10"
   :yellow "text-yellow-800 bg-yellow-50 ring-yellow-600/20"
   :red    "text-red-700 bg-red-50 ring-red-600/10"})

(defui status-label
  [{:keys [label color]}]
  (let [color-class (status-label-color->class color)]
    ($ :p {:class [color-class "rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset"]}
      label)))

(defui stacked-list-item
  [{:keys [title
           sub-titles
           right-cta-button
           right-menu]}]
  ($ :li {:class "flex items-center justify-between gap-x-6 py-5"}
    ($ :div {:class "min-w-0"}
      title
      ($ :div {:class "mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500"}
        (for [elem (interpose
                     ($ :svg {:viewBox "0 0 2 2", :class "h-0.5 w-0.5 fill-current"} ($ :circle {:cx 1, :cy 1, :r 1}))
                     sub-titles)]
          ($ :<> {:key (random-uuid)} elem))))
    ($ :div {:class "flex flex-none items-center gap-x-4"}
      right-cta-button
      (when right-menu
        (let [{:keys [items]} right-menu]
          ($ h.ui/Menu {:as "div" :class "relative flex-none"}
            ($ h.ui/Menu.Button {:class "-m-2.5 block p-2.5 text-gray-500 hover:text-gray-900"}
              ($ :span {:class "sr-only"} "Open options")
              ($ icons/EllipsisVerticalIcon {:class "h-5 w-5" :aria-hidden true}))
            ($ h.ui/Transition
              {:as        react/Fragment,
               :enter     "transition ease-out duration-100"
               :enterFrom "transform opacity-0 scale-95"
               :enterTo   "transform opacity-100 scale-100"
               :leave     "transition ease-in duration-75"
               :leaveFrom "transform opacity-100 scale-100"
               :leaveTo   "transform opacity-0 scale-95"}
              ($ h.ui/Menu.Items {:class "absolute right-0 z-10 mt-2 w-32 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none"}
                (for [{:keys [key label href on-click]} items]
                  ($ h.ui/Menu.Item {:key (or key label)}
                    (fn [render-props]
                      (let [active (.-active render-props)]
                        ($ :a {:class    (str (when active "bg-gray-50") " block px-3 py-1 text-sm leading-6 text-gray-900")
                               :href     href
                               :on-click on-click}
                          label
                          ($ :span {:class "sr-only"} "project.name"))))))))))))))

(defui stacked-list
  [{:keys [children]}]
  ($ :ul {:role "list" :class "divide-y divide-gray-100"}
    children))

(defui date-comp
  [{:keys [date]}]
  (let [instant (instant/of-epoch-milli date)
        iso-date (date-time-formatter/format date-time-formatter/iso-instant instant)
        friendly-date (-> "yyyy-MM-dd HH:mm:ss"
                        date-time-formatter/of-pattern
                        (date-time-formatter/format (instant/at-zone instant (zone-id/of "UTC"))))]
    ($ :time {:dateTime iso-date} friendly-date)))

(defui tabs
  [{:keys [selected-id tabs container-class]}]
  ($ :div {:class (str "hidden sm:block " container-class)}
    ($ :div {:class "border-b border-gray-200"}
      ($ :nav {:class "-mb-px flex space-x-8" :aria-label "Tabs"}
        (for [{:keys [id label href]} tabs
              :let [current? (= id selected-id)]]
          ($ :a {:key   id
                 :href  href
                 :class (str
                          (if current? "border-indigo-500 text-indigo-600" "border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700")
                          "whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium")}
            label))))))

(defui form-input
  [{:keys [label id] :as input-opts}]
  ($ :<>
    ($ :label {:htmlFor id
               :class   "block text-sm font-medium leading-6 text-gray-900"}
      label)
    ($ :div {:class "mt-2"}
      ($ :input
        (merge
          {:type  "text"
           :name  id
           :id    id
           :class "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"}
          (dissoc input-opts :label))))))

(defui list-header
  [{:keys [title action-button-right]}]
  ($ :div {:class "border-b border-gray-200 pb-5 sm:flex sm:items-center sm:justify-between"}
    ($ :h3 {:class "text-base font-semibold leading-6 text-gray-900"} title)
    ($ :div {:class "mt-3 sm:ml-4 sm:mt-0"}
      ($ buton (merge {:size 4 :as :a} action-button-right) (:text action-button-right)))))

(def deployment-end-states #{:deployment.status/succeeded :deployment.status/failed})

(let [status->info {:deployment.status/succeeded {:label "Ready" :color :green}
                    :deployment.status/running   {:label "Creating" :color :gray}
                    :deployment.status/failed    {:label "Failed" :color :red}
                    :deployment.status/deleting  {:label "Deleting" :color :gray}}]
  (defui deployment-status
    [{:keys [deployment]}]
    (let [{:deployment/keys [status]} deployment]
      (when-let [info (status->info status)]
        ($ status-label info)))))

(defui zone-selector
  [{:keys [input-id default-value]}]
  (let [{:keys [data]} (utils/use-query {:queryKey ["ListZones"]
                                         :queryFn  #(utils/request {:method :post :url "/api/ListZones"})})
        zones (-> data :body :data)
        options (map (fn [{:zone/keys [id name]}]
                       {:id id :label name}) zones)]
    ($ combobox
      {:input-label   "Zone"
       :input-name    (or input-id (utils/write :zone/id))
       :default-value default-value
       :options       options})))
