(ns index
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(defn- get-json
  [url]
  (json/read-str (:body (client/get url)) :key-fn keyword))

(defn- space-results-from-page
  ([wiki-base-url page-path]
   (let [results (get-json (str wiki-base-url page-path))
         next-page-path (get-in results [:_links :next])]
     (if next-page-path
       (lazy-cat (:results results) (space-results-from-page wiki-base-url next-page-path))
       (:results results)))))

(defn space-results
  "Walk a wiki space"
  [wiki-base-url space-key]
  (space-results-from-page wiki-base-url (str "/rest/api/content?spaceKey=" space-key "&expand=body.view")))

(defn index-page
  [page])

(defn index-space
  [wiki-base-url space-key]
  (map index-page (space-results wiki-base-url space-key)))
