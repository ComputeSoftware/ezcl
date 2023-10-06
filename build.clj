(ns build
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.tools.build.api :as b]))

(def lib 'cs/ezcl)
(def main-ns 'cs.ezcl.core)
(def version "v1.0.0")
(def target-dir "target")
(def class-dir (str target-dir "/" "classes"))
(def uber-file (format "%s/%s-standalone.jar" target-dir (name lib)))
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean
  "Delete the build target directory"
  [_]
  (println (str "Cleaning " target-dir))
  (b/delete {:path target-dir}))

(defn prep [_]
  (println "Writing Pom...")
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :src-dirs  ["src/clj"]})
  (b/copy-dir {:src-dirs   ["src/clj" "resources" "env/prod/resources" "env/prod/clj"]
               :target-dir class-dir}))

(defn build-cljs [_]
  (println "npx shadow-cljs release app...")
  (let [{:keys [exit], :as s} (process/shell "npx shadow-cljs release app")]
    (when-not (zero? exit) (throw (ex-info "could not compile cljs" s)))
    (fs/delete-tree "resources/public/js")
    (fs/copy-tree "target/classes/cljsbuild/public/js" "resources/public/js")))

(defn uber [_]
  (println "Compiling Clojure...")
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src/clj" "resources" "env/prod/resources" "env/prod/clj"]
                  :class-dir class-dir})
  (build-cljs {})
  (println "Making uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :main      main-ns
           :basis     basis}))

(defn all [_]
  (do (clean nil) (prep nil) (uber nil)))
