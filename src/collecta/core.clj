(ns collecta.core
  (:use (xmpp-clj))
  (:require [clojure.xml :as xml])
  (:import (org.jivesoftware.smack ConnectionConfiguration
                                   XMPPConnection
                                   PacketListener)
           (org.jivesoftware.smack.filter PacketFilter
                                          MessageTypeFilter)
           (org.jivesoftware.smack.packet IQ
                                          IQ$Type
                                          Message
                                          Message$Type)
           (java.io ByteArrayInputStream) ))

(def *COLLECTA-CONNECTION-URL* "guest.collecta.com")
(def *COLLECTA-SEARCH-URL* "search.collecta.com")
(declare *collecta-connection*)

(defn- create-connection
  (new XMPPConnection
       (new ConnectionConfiguration *COLLECTA-CONNECTION-URL*)))

(defn- establish-connection
  []
  (let [conn (create-connection)]
    (doto conn
      (.connect)
      (.loginAnonymously)
      (.addPacketListener (packet-listener) (packet-filter)))))

(defn packet-filter
  []
  (proxy [PacketFilter] [] 
    (accept [packet] true)))

(defn- extract-event-packet
  [packet]
  (.getExtension packet "event" "http://jabber.org/protocol/pubsub#event"))

(defn packet-to-xml
  [packet-event]
  (clojure.xml/parse
   (ByteArrayInputStream. (.getBytes (.toXML pe) "UTF-8"))))

(defn packet-listener
  [processing-fn]
  (proxy [PacketListener] []
    (processPacket
     [packet]
     (let [pe (extract-event-packet-extension packet)]
       (if (not (nil? pe))
         (apply processing-fn (packet-to-xml pe)))))))

;; todo: Build the xml message with prxml.
;;       Allow more than one query term to be submitted.
(defn iq-search-packet
  [term jid apikey]
  (proxy [IQ] []
    (getChildElementXML
     []
     (str  "<pubsub xmlns='http://jabber.org/protocol/pubsub'>" +
           "<subscribe jid='" jid "' node='search'/>" 
           "<options>" 
           "<x xmlns='jabber:x:data' type='submit'>" 
           "<field var='FORM_TYPE' type='hidden'>" 
           "<value>http://jabber.org/protocol/pubsub#subscribe_options</value>" 
           "</field>"
           "<field var='x-collecta#apikey'>"
           "<value>" apikey "</value>" 
           "</field>"
           "<field var='x-collecta#query'>"
           "<value>" term "</value>"
           "</field>" 
           "</x></options>"
           "</pubsub>"))))

(def iq-packet (iq-search-packet "$AAPL" (.getUser conn) "956718aa639f309c0c3d3dd221c9da2d"))

(doto iq-packet
  (.setType IQ$Type/SET)
  (.setFrom (.getUser conn))
  (.setTo *COLLECTA-SEARCH-URL*))

(.sendPacket conn iq-packet)

