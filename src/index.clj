(ns index
  (:require [es :as es]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.walk :refer [stringify-keys]]
            [hickory.core :as hickory])
  (:import (org.elasticsearch.client RestHighLevelClient RequestOptions)
           (org.elasticsearch.action.index IndexRequest)
           (org.elasticsearch.client.indices CreateIndexRequest GetIndexRequest)
           (org.jsoup.nodes Document)))

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
  (space-results-from-page wiki-base-url (str "/rest/api/content?spaceKey=" space-key "&expand=body.view,space")))

(defn page->document
  [page]
  {:title      (:title page)
   :content    (.text #^Document (hickory/parse (get-in page [:body :view :value])))
   :path       (get-in page [:_links :webui])
   :space      (get-in page [:space :key])
   :space-name (get-in page [:space :name])})

(def base-index-settings
  {:analysis {:analyzer {:html-content {:type "custom"
                                        :tokenizer "standard"
                                        :filter ["lowercase" "asciifolding" "english-stop" "english-stemmer"]}}
              :filter {:english-stop {:type "stop"
                                      :stopwords "_english_"}
                       :english-stemmer {:type "stemmer"
                                         :name "english"}}}
   :index {:blocks {:read_only false
                    :read_only_allow_delete false}}})

(def index-mappings
  {:properties {:title {:type "text"
                        :store true}
                :content {:type "text"
                          :analyzer "html-content"}
                :path {:type "keyword"
                       :index false
                       :store true}
                :space {:type "keyword"
                        :store true}
                :space-name {:type "text"
                             :store true}}})

(defn ensure-index
  [^RestHighLevelClient client replicas]
  (let [indices-client (.indices client)]
    (if (not (.exists indices-client (GetIndexRequest. (into-array ["wiki"])) RequestOptions/DEFAULT))
      (.create (.indices client) (-> (CreateIndexRequest. "wiki")
                                     (.settings #^java.util.Map (into (array-map) (stringify-keys (assoc base-index-settings :number_of_replicas replicas))))
                                     (.mapping #^java.util.Map(into (array-map) (stringify-keys index-mappings)))) RequestOptions/DEFAULT))))

(defn index-page
  [^RestHighLevelClient client page]
  (.index client (-> (IndexRequest.)
                     #^IndexRequest (.index "wiki")
                     (.id (:id page))
                     (.source #^java.util.Map (into (array-map) (stringify-keys (page->document page))))) RequestOptions/DEFAULT))

(defn index-space
  [wiki-base-url space-key]
  (let [es-client (es/es-client)]
    (ensure-index es-client 0)
    (map (partial index-page es-client) (space-results wiki-base-url space-key))))
