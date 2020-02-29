(ns es
  (:import (org.elasticsearch.client RestHighLevelClient RestClient)
           (org.apache.http HttpHost)))

(defn ^RestHighLevelClient es-client
  []
  (RestHighLevelClient. (RestClient/builder #^"[Lorg.apache.http.HttpHost;" (into-array [(HttpHost. "localhost" 9200 "http")]))))