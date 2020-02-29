(ns search
  (:require [es :as es])
  (:import (org.elasticsearch.action.search SearchRequest)
           (org.elasticsearch.index.query QueryBuilders)
           (org.elasticsearch.search.fetch.subphase.highlight HighlightBuilder HighlightField)
           (org.elasticsearch.search.builder SearchSourceBuilder)
           (org.elasticsearch.client RequestOptions)
           (org.elasticsearch.search SearchHit)
           (org.elasticsearch.common.document DocumentField)
           (org.elasticsearch.common.text Text)))

(defn ^SearchRequest search-request
  [text]
  (-> (SearchRequest.)
      (.indices (into-array (list "wiki")))
      (.source (-> (SearchSourceBuilder.)
                   (.query (QueryBuilders/multiMatchQuery text (into-array ["title" "content"])))
                   (.storedFields ["space" "space-name" "title" "path"])
                   (.highlighter (-> (HighlightBuilder.)
                                     (.field "content")))))))


(defn search
  [text]
  (let [es-client (es/es-client)
        search-response (.search es-client (search-request text) RequestOptions/DEFAULT)]
    (map (fn [^SearchHit hit]
           (merge
             {:highlight (map #(.string #^Text %1) (.getFragments #^HighlightField (get (.getHighlightFields hit) "content")))}
             (into {} (map (fn [[key ^DocumentField field]]
                             [(keyword key) (.getValue field)]) (into {} (.getFields hit)))))) (.getHits (.getHits search-response)))))
