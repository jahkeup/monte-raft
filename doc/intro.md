# Introduction to monte-raft

This program is a basic implementation of RAFT and is intended to
establish the probability of leader elections in certain cases,
establish the probability of proper state replication, and establish
the probability of overall availability. A monte-carlo simulation with
random elements will be employed in order to produce a real world
probability of each of the events.

## How

On every event, the system will check for each condition and tally up
the event occurrences and number of total events so that the system's
rate of failure, expressed as a probability may be determined.

# Implementation decisions

zmq (ZeroMQ) will be used to establish connections between nodes,
timers will determine the 'up' status of other nodes, and the leader
will keep timers against all other nodes. Every node will have a
'node-map' of the nodes that are currently in the system. Upon a clean
departure or addition of a node, the running system will replicate the
change to the 'node-map' and update.

