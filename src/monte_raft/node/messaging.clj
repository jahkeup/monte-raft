(ns monte-raft.node.messaging
  (:require [clojure.string :as s]
            [zeromq.zmq :as zmq]))

(def ^:const command-pairs
  "Possible message pairs that can be sent over the control socket"
  [[:ping :pong]           ; Heartbeat
   [:elect :vote :novote]  ; Election
   [:follow :following]    ; Leadership change, instruct to follow
   [:confirm :confirmed :unconfirmed]   ; State Update: Confirm update
   [:commit :committed :uncommitted]])  ; State Update: Commit last update

(defn to-command-fmt
  "Turn a message string into a normalized keyword"
  [msg]
  (if msg
    (if (keyword? msg)
     msg
     (keyword (s/lower-case (str msg))))))

(defn get-command-vec [msg]
  (if-let [command-set (filter #(= (first %) msg) command-pairs)]
    (first command-set)))

(defn valid-cmd? [msg]
  (let [cmd (to-command-fmt msg)]
    (not (nil? (get-command-vec cmd)))))

(defn responses-for [msg]
  (let [cmd (to-command-fmt msg)]
    (if (valid-cmd? cmd)
      (rest (get-command-vec cmd)))))

(defn valid-response?
  ([resp]
     (let [resp (to-command-fmt resp)]
       (boolean (some #{resp} (flatten (map #(rest %) command-pairs))))))
  ([msg resp]
     (if (valid-cmd? msg)
       (let [cmd (to-command-fmt msg)
             resp (to-command-fmt resp)]
         (boolean (some #{resp} (responses-for cmd)))))))

(defn command-to-str [cmd]
  (if (or (valid-cmd? cmd) (valid-response? cmd))
    (name cmd)))

