(ns es
  (:import (org.elasticsearch.client RestHighLevelClient RestClient)
           (org.apache.http HttpHost)))

(def localhost-default
  (HttpHost. "localhost" 9200 "http"))

(defn ^RestHighLevelClient es-client
  [^HttpHost elasticsearch-host]
  (RestHighLevelClient. (RestClient/builder #^"[Lorg.apache.http.HttpHost;" (into-array [elasticsearch-host]))))