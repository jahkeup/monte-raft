(ns monte-raft.node.state
  (:require [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.timer :as timer]))

(defn make-node-options
  "Create a working node config map based on the id"
  [id]
  {:node-id (keyword (str id))
   :control-binding (format "inproc://%s-control" id)
   ;; Depending on timing conditions, zmq may like us to bind on a TCP
   ;; port here instead, otherwise subscribers may not subscribe to a
   ;; *real* bound socket.
   :leader-binding (format "inproc://system-leader-L%s" id)
   :publish-binding (format "inproc://system-state-updates-L%s" id)
   :timeout (timer/random-timeout 1000 2000)

   ;; Node state objects.
   :state
   {:transient (atom nil)
    :current (atom nil)
    :term (atom 0)
    :confirmed (atom false)}

   :kill-codes
   {:control (keyword (format "%s-control-worker" id))
    :leader (keyword (format "%s-leader-worker" id))
    :state (keyword (format "%s-state-worker" id))
    :logger (keyword (format "%s-logger-worker" id))}})

(defn -make-node-cluster-map
  "Generate a map of 1-x nodes (for use in cluster)"
  [names]
  (reduce
    #(assoc %1 (keyword %2) (make-node-options %2))
    {} names))

;;
;; Many of these variables will come from the node's config map
;; instead, but are left until everything has been converted.
;;
;; IE: Deprecated!
;;

(def ^:dynamic node-id
  "The node identifier"
  nil)

(def cluster "Cluster node addresses"
  (atom (-make-node-cluster-map (map #(format "n%s" %1) (range 1 4)))))

(defn confirmable? "Is the current state confirmable?"
  [{:keys [transient current] :as state}]
  (not (nil? @transient)))

(defn confirmed! "Mark transient state confirmed"
  [{:keys [confirmed] :as state}]
  (reset! confirmed true))

(defn confirmed? "Has the current transient state been confirmed?"
  [{:keys [confirmed] :as state}]
  (true? @confirmed))

(defn commit!
  "Terminate state chain, update state and clear transient and
  confirmation flag" [{:keys [transient confirmed current] :as state}]
  (reset! current @transient)
  (reset! confirmed false)
  (reset! transient nil))
