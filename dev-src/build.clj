(ns build
  (:require [badigeon.javac :as javac]
            [badigeon.compile :as compile]
            [clj.native-image :as native-image-builder]
            [clojure.tools.deps.alpha.reader :as deps-reader]
            [clojure.string :as cs]
            [clojure.tools.namespace.find :refer [find-namespaces-in-dir]])
  (:import (java.io File)))

(def deps-content (deps-reader/slurp-deps "deps.edn"))
(def java-src-folder (get-in deps-content [:build :java-source-paths]))
(def javac-options (get-in deps-content [:build :javac-options]))
(def compile-path (get-in deps-content [:build :compile-path]))
(def main-ns (get-in deps-content [:build :main-ns]))
(def native-image-path (get-in deps-content [:build :native-image-path]))
(def native-image-options (get-in deps-content [:build :native-image-options]))
(def native-image-compiler-options (get-in deps-content [:build :native-image-compiler-options]))

(defn compile-java
  []
  (when (and java-src-folder compile-path)
    (javac/javac java-src-folder {:compile-path  compile-path
                                  :javac-options javac-options})))

(defn- native-image-classpath
  "Returns the current tools.deps classpath string, minus clj.native-image and plus *compile-path*."
  []
  (as-> (System/getProperty "java.class.path") $
        (cs/split $ (re-pattern (str File/pathSeparatorChar)))
        (remove #(cs/includes? % "tar.gz") $) ;; exclude ourselves
        (cons compile-path $) ;; prepend compile path for classes
        (cs/join File/pathSeparatorChar $)))

(defn- munge-class-name [class-name]
  (cs/replace class-name "-" "_"))

(defn native-image
  []
  (let [nat-img-path (if native-image-path
                       native-image-path
                       (native-image-builder/native-image-bin-path))]
    (compile-java)
    (compile/compile (symbol main-ns) {:compile-path compile-path
                                       :compiler-options native-image-compiler-options})

    (System/exit
      (native-image-builder/exec-native-image
        nat-img-path
        native-image-options
        (native-image-classpath)
        (munge-class-name main-ns)))))