(ns monte-raft.node
  (:require [monte-raft.node.state :as node-state]
            [monte-raft.node.control-worker :refer [control-worker]]
            [monte-raft.node.state-worker :refer [state-worker]]
            [monte-raft.node.leader-worker :refer [leader-worker]]
            [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.worker :as worker]
            [monte-raft.client :as client]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]
            [clojure.core.async :as async
             :refer [chan close! >! >!! <! <!! go go-loop]]))

(def cluster-config {:node-id :cluster
                        :kill-codes {:system :system}})

(defn node
  "Start a node

   node-id: the unique id of this host

   opts: node config option map, create with
  monte-raft.node.state/make-node-options"
  [{:keys [node-id control-binding] :as node-config}]
  (log/tracef "Spawning node '%s' using options: \n%s" node-id
    (with-out-str (clojure.pprint/pprint node-config)))
  (binding [node-state/node-id node-id
            node-state/state (atom nil)
            node-state/term (atom 0)
            node-state/transient-state (atom nil)
            node-state/confirmed (atom false)]
    (worker/with-comm-sock node-config
      (log/debugf "Starting node %s" node-id)
      (let [running-worker (worker/start (control-worker node-config))]
        (<!! running-worker)
        (log/infof "Control has exited. Node (%s) shutting down." node-id)))
    :terminated))

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
  (let [rand-one (random-node (cluster-map))]
    (set-leader cluster-map
      #(assoc % :leader-id (keyword (:node-id rand-one))))))

(defn stop-system-nodes
  "Kill all nodes" []
  (log/trace "System terminating all nodes.")
  (let [get-kill-code #(get-in (last %) [:kill-codes :control])]
    (doall (for [node @node-state/cluster]
             (worker/signal-terminate (get-kill-code node))))))

(defn start-system
  "Based on the cluster defined in the node.state, start all the nodes and run."
  []
  (worker/with-comm-sock cluster-config
    (client/start-nrepl)
    (log/infof "System starting.")
    (doall
      (for [node-id (keys @node-state/cluster)]
        (let [node-config (node-id @node-state/cluster)]
          (log/infof "System starting node %s" node-id)
          (worker/start (node node-config)))))
    (log/infof "System has started all nodes.")
    (worker/until-worker-terminate cluster-config :system
      (Thread/sleep 10))
    (log/infof "System exiting.")
    (stop-system-nodes)
    (client/stop-nrepl))
  :system-terminated)

(defn stop-system
  "Kill cluster" []
  (worker/signal-terminate :system))
