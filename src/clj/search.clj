(ns search
  (:require [es :as es]
            [hickory.core :as hickory]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.term.colors :as colors])
  (:import (org.elasticsearch.action.search SearchRequest SearchResponse)
           (org.elasticsearch.index.query QueryBuilders)
           (org.elasticsearch.search.fetch.subphase.highlight HighlightBuilder HighlightField)
           (org.elasticsearch.search.builder SearchSourceBuilder)
           (org.elasticsearch.client RequestOptions)
           (org.elasticsearch.search SearchHit)
           (org.elasticsearch.common.document DocumentField)
           (org.elasticsearch.common.text Text)
           (org.apache.http HttpHost))
  (:gen-class))

(defn ^SearchRequest search-request
  [text space]
  (-> (SearchRequest.)
      (.indices #^"[Ljava.lang.String;" (into-array (list "wiki")))
      (.source (-> (SearchSourceBuilder.)
                   (.query (-> (QueryBuilders/boolQuery)
                               (.must (QueryBuilders/multiMatchQuery text #^"[Ljava.lang.String;" (into-array ["title" "content"])))
                               (cond->
                                 space (.filter (QueryBuilders/termQuery "space" space)))))
                   (.storedFields ["space" "space-name" "title" "url"])
                   (.highlighter (-> (HighlightBuilder.)
                                     (.field "content")
                                     (.fragmentSize (int 30))))))))

(defn parse-response
  [^SearchResponse search-response]
  (map (fn [^SearchHit hit]
         (merge
           {:highlights (vec (map (fn [^Text text]
                                   (map (fn [html-frag]
                                          (let [h (hickory/as-hickory html-frag)]
                                            (if (and (map? h) (= (:tag h) :em))
                                              {:highlight true
                                               :text (:content h)}
                                              h))) (hickory/parse-fragment (.string text)))) (.getFragments #^HighlightField (get (.getHighlightFields hit) "content"))))}
           (into {} (map (fn [[key ^DocumentField field]]
                           [(keyword key) (.getValue field)]) (into {} (.getFields hit)))))) (.getHits (.getHits search-response))))

(defn format-fragment
  [fragment]
  (string/join (map (fn [sub-fragment]
                      (if (:highlight sub-fragment)
                        (colors/reverse-color (:text sub-fragment))
                        sub-fragment)) fragment)))

(defn format-highlight
  [highlights]
  (string/join "\n" (map #(str "- " (format-fragment %1)) highlights)))

(defn format-header
  [result]
  (str (colors/bold (:title result)) " | " (:url result)))

; TODO better format for small terminal sizes
(defn format-hit
  [hit]
  (string/join "\n" [(format-header hit)
                    (format-highlight (:highlights hit))]))

(defn format-results
  [results]
  (string/join "\n\n" (map format-hit results)))

(defn search
  [text {elasticsearch-host :elasticsearch-host
         space :space}]
  (with-open [es-client (es/es-client elasticsearch-host)]
    (parse-response (.search es-client (search-request text space) RequestOptions/DEFAULT))))

(def cli-options
  [["-s" "--space" "Space key"
    :parse-fn identity
    :default nil
    :required false]
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
      (<= 1 (count arguments))
      {:query (string/join " " arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn -main [& args]
  (let [{:keys [query options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (println (format-results (search query options))))))