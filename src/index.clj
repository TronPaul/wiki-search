(ns index
  (:require [es :as es]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.walk :refer [stringify-keys]]
            [hickory.core :as hickory]
            [clojure.string :as string]
            [clojure.tools.cli :as cli])
  (:import (org.elasticsearch.client RestHighLevelClient RequestOptions)
           (org.elasticsearch.action.index IndexRequest)
           (org.elasticsearch.client.indices CreateIndexRequest GetIndexRequest)
           (org.jsoup.nodes Document)
           (org.apache.http HttpHost)))

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
  [wiki-base-url page]
  {:title      (:title page)
   :content    (.text #^Document (hickory/parse (get-in page [:body :view :value])))
   :path       (get-in page [:_links :webui])
   :url        (str wiki-base-url (get-in page [:_links :webui]))
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
                :url {:type "keyword"
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
  [^RestHighLevelClient client wiki-base-url page]
  (.index client (-> (IndexRequest.)
                     #^IndexRequest (.index "wiki")
                     (.id (:id page))
                     (.source #^java.util.Map (into (array-map) (stringify-keys (page->document wiki-base-url page))))) RequestOptions/DEFAULT))

;todo batch indexing
(defn index-space
  [wiki-base-url space-key]
  (with-open [es-client (es/es-client es/localhost-default)]
    (ensure-index es-client 0)
    (doall (map (partial index-page es-client wiki-base-url) (filter #(not (nil? (get-in %1 [:body :view :value]))) (space-results wiki-base-url space-key))))))

(def cli-options
  [["-s" "--space" "Space key"
    :parse-fn identity
    :required true]
   ["-e" "--elasticsearch-url" "Elasticsearch URL"
    :id :elasticsearch-host
    :default es/localhost-default
    :parse-fn #(HttpHost/create %1)
    :required false]])

(defn usage [options-summary]
  (->> ["Usage: wiki-search [options] [query text...]"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      ;(:help options) ; help => exit OK with usage summary
      ;{:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (= 1 (count arguments))
      {:confluence-base-url (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn -main [& args]
  (let [{:keys [confluence-base-url options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (index-space confluence-base-url (:space options)))))
