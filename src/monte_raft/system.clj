(ns monte-raft.system
  (:require [clojure.core.async :as async :refer [<!!]]
            [monte-raft.node :as node]
            [monte-raft.node.worker :as worker]
            [monte-raft.client :as client]
            [monte-raft.node.state :as node-state]
            [taoensso.timbre :as log]))

(def cluster-config {:node-id :cluster
                     :kill-codes {:system :system}})

;; http://blog.jayfields.com/2011/08/clojure-apply-function-to-each-value-of.html
(defn update-values "Update each key in a map using f"
  [m f & args]
  (reduce (fn [r [k v]] (assoc r k (apply f v args))) {} m))

(defn random-node
  "Return the config map for a random node in cluster" [cluster-map]
  (get cluster-map
    (rand-nth (keys cluster-map))))

(defn set-leader "Return cluster-map with leader-id (initial) set."
  [cluster-map leader-id]
  (update-values cluster-map #(assoc % :leader-id (keyword leader-id))))

(defn config-random-leader [cluster-map]
  (let [rand-leader (:node-id (random-node cluster-map))]
    [rand-leader (set-leader cluster-map rand-leader)]))

(defn stop-system-nodes
  "Kill all nodes" []
  (log/trace "System terminating all nodes.")
  (doall (for [[node-id node-config] @node-state/cluster]
           (worker/signal-terminate node-config :control))))

(defn save-worker! [node-id running-worker]
  (reset! node-state/cluster (assoc-in @node-state/cluster
                               [:node-id :running-worker] running-worker)))

(defn clean-worker! [node-id]
  (swap! node-state/cluster
    #(assoc % node-id (dissoc (node-id %) :running-worker))))

(defn start-system
  "Based on the cluster defined in the node.state, start all the nodes and run."
  []
  (worker/with-comm-sock cluster-config
    (try
      (client/start-nrepl)
      (log/infof "System starting.")
      (let [[leader-id cluster-nodes] (config-random-leader @node-state/cluster)]
        (log/infof "System selected '%s' as initial leader." leader-id)
        (doall (for [node-id (take 1 (keys cluster-nodes))]
                 (let [node-config (node-id cluster-nodes)]
                   (log/infof "System starting node %s" node-id)
                   (save-worker! node-id (worker/start (node/node node-config)))))))
      (log/infof "System has started all nodes from config:\n%s"
        (with-out-str (clojure.pprint/pprint @node-state/cluster)))
      (worker/until-worker-terminate cluster-config :system
        (Thread/sleep 1000))
      (catch Throwable e (clojure.stacktrace/print-cause-trace e))
      (finally (do (stop-system-nodes)
                   (client/stop-nrepl)))))
  (log/infof "System exiting.")
  :system-terminated)

(defn stop-system
  "Kill cluster" []
  (worker/signal-terminate :system cluster-config))
