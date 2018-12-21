(ns jute.server
  (:require
   [jute.core :as jute]
   [cheshire.core :as json]
   [clj-yaml.core :as yaml]
   [ring.middleware.resource :refer [wrap-resource]]
   [org.httpkit.server :as server]))


(defonce server (atom nil))
(defn stop []
  (when-let [s @server] (s) (reset! server nil)))

(defn handle [{meth :request-method hs :headers :as req}]
  (if (= :post meth)
    (let [{j :jute s :source} (json/parse-string (slurp (:body req)) keyword)
          prg (yaml/parse-string j)
          subj (yaml/parse-string s)
          res ((jute/compile prg) subj)]
      {:status 200
       :headers {"content-type" "text/yaml"}
       :body (yaml/generate-string res :flow-style :block)})
    {:status 404}))

(def dispatch
  (-> #'handle
      (wrap-resource "public")))

(defn start [port]
  (stop)
  (reset! server (server/run-server #'dispatch {:port (or port 7896)})))


(comment
  (start 7896)

  )
