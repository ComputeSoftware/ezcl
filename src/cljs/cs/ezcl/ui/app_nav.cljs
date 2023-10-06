(ns cs.ezcl.ui.app-nav
  (:require
    [cs.ezcl.ui.routing :as routing]
    [kwill.uix-state :as ss]
    [uix.core :refer [$ defui]]
    ["@heroicons/react/24/outline" :as icons]
    ["@tanstack/react-query" :as t.rq]
    ["@headlessui/react" :as h.ui]
    ["@clerk/clerk-react" :as clerk]
    ["react" :as react]))

(def navigation
  (->> [{:name "Dashboard" :page :page/root :icon icons/HomeIcon}
        {:name "Instance Groups" :page :page/instance-group-list :icon icons/UsersIcon}
        {:name "Zones" :page :page/zone-list :icon icons/FolderIcon}]
    (map (fn [{:keys [page] :as nav}]
           (assoc nav :href (routing/href {:route/name page}))))))

(defn user-navigation
  [{:keys [auth]}]
  [{:name "Your profile" :href "#"}
   {:name "Signout" :on-click #(.signOut auth)}])

(defui nav-item
  [{:keys [name href icon current]}]
  ($ :a {:href  href
         :class (str (if current
                       "bg-indigo-700 text-white "
                       "text-indigo-200 hover:text-white hover:bg-indigo-700 ")
                  "group flex gap-x-3 rounded-md p-2 text-sm leading-6 font-semibold")}
    ($ icon {:class       (str (if current "text-white" "text-indigo-200 group-hover:text-white ")
                            "h-6 w-6 shrink-0")
             :aria-hidden true})
    name))

(defui sidebar
  []
  (let [route-name (ss/sub :sub.route/name)]
    ($ :div {:class "hidden lg:fixed lg:inset-y-0 lg:z-50 lg:flex lg:w-72 lg:flex-col"}
      ($ :div {:class "flex grow flex-col gap-y-5 overflow-y-auto bg-indigo-600 px-6 pb-4"}
        ($ :div {:class "flex h-16 shrink-0 items-center"}
          ($ :img {:src "https://tailwindui.com/img/logos/mark.svg?color=white" :alt "Your Company" :class "h-8 w-auto"}))
        ($ :nav {:class "flex flex-1 flex-col"}
          ($ :ul {:role "list" :class "flex flex-1 flex-col gap-y-7"}
            ($ :li {}
              ($ :ul {:role "list" :class "-mx-2 space-y-1"}
                (for [nav-map navigation]
                  ($ nav-item (assoc nav-map
                                      :current (= (:page nav-map) route-name)
                                      :key (:href nav-map))))))
            ($ :li {:class "mt-auto"}
              ($ :a {:href  (routing/href {:route/name :page/settings})
                     :class "group -mx-2 flex gap-x-3 rounded-md p-2 text-sm font-semibold leading-6 text-indigo-200 hover:bg-indigo-700 hover:text-white"}
                ($ icons/Cog6ToothIcon {:class       "h-6 w-6 shrink-0 text-indigo-200 group-hover:text-white"
                                        :aria-hidden "true"})
                "Settings"))))))))

(defui profile-dropdown
  []
  (let [auth (clerk/useAuth)
        user (clerk/useUser)
        user-display-name (.. user -user -primaryEmailAddress -emailAddress)]
    ($ h.ui/Menu {:as "div" :class "relative"}
      ($ h.ui/Menu.Button {:class "-m-1.5 flex items-center p-1.5"}
        ($ :span {:class "sr-only"} "Open user menu")
        ($ :span {:class "hidden lg:flex lg:items-center"}
          ($ :span {:aria-hidden "true" :class "ml-4 text-sm font-semibold leading-6 text-gray-900"} user-display-name)
          ($ icons/ChevronDownIcon {:aria-hidden "true" :class "ml-2 h-5 w-5 text-gray-400"})))
      ($ h.ui/Transition
        {:as        react/Fragment
         :enter     "transition ease-out duration-100"
         :enterFrom "transform opacity-0 scale-95"
         :enterTo   "transform opacity-100 scale-100"
         :leave     "transition ease-in duration-75"
         :leaveFrom "transform opacity-100 scale-100"
         :leaveTo   "transform opacity-0 scale-95"}
        ($ h.ui/Menu.Items {:class "absolute right-0 z-10 mt-2.5 w-32 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none"}
          (for [{:keys [name href on-click]} (user-navigation {:auth auth})]
            ($ h.ui/Menu.Item {:key name}
              (fn [js-map]
                ($ :a {:href     href
                       :on-click on-click
                       :class    (str (when (.-active js-map) "bg-gray-50 ")
                                   "block px-3 py-1 text-sm leading-6 text-gray-900 cursor-pointer")}
                  name)))))))))

(defui top-nav
  []
  ($ :div {:class "sticky top-0 z-40 flex h-16 shrink-0 items-center gap-x-4 border-b border-gray-200 bg-white px-4 shadow-sm sm:gap-x-6 sm:px-6 lg:px-8"}
    ($ :button {:type  "button"
                :class "-m-2.5 p-2.5 text-gray-700 lg:hidden"}
      ($ :span {:class "sr-only"} "Open sidebar")
      ($ icons/Bars3Icon {:aria-hidden "true" :class "h-6 w-6"}))
    ($ :div {:aria-hidden "true" :class "h-6 w-px bg-gray-900/10 lg:hidden"})
    ($ :div {:class "flex flex-1 gap-x-4 self-stretch lg:gap-x-6"}
      ($ :div {:class "relative flex flex-1"})
      ($ :div {:class "flex items-center gap-x-4 lg:gap-x-6"}
        ($ profile-dropdown)))))
