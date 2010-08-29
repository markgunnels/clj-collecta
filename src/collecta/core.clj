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

(defn- packet-filter
  []
  (proxy [PacketFilter] [] 
    (accept [packet] true)))

(defn- extract-event-packet-extension
  [packet]
  (.getExtension packet "event" "http://jabber.org/protocol/pubsub#event"))

(defn- packet-to-xml
  [packet-event]
  (clojure.xml/parse
   (ByteArrayInputStream. (.getBytes (.toXML packet-event) "UTF-8"))))

(defn- packet-listener
  [on-result-fn]
  (proxy [PacketListener] []
    (processPacket
     [packet]
     (let [pe (extract-event-packet-extension packet)]
       (if (not (nil? pe))
         (apply on-result-fn (packet-to-xml pe)))))))

;; todo: Build the xml message with prxml.
;;       Allow more than one query term to be submitted.
(defn- iq-search-packet
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

(defn- create-connection
  []
  (new XMPPConnection
       (new ConnectionConfiguration *COLLECTA-CONNECTION-URL*)))

(defn- establish-connection
  [on-result-fn]
  (let [conn (create-connection)]
    (doto conn
      (.connect)
      (.loginAnonymously)
      (.addPacketListener (packet-listener on-result-fn) (packet-filter)))
    conn))

(defn- make-iq-packet
  [api-key term conn]
  (let [iq-packet (iq-search-packet term (.getUser conn) api-key)]
    (doto iq-packet
      (.setType IQ$Type/SET)
      (.setFrom (.getUser conn))
      (.setTo *COLLECTA-SEARCH-URL*))))

(defn search-collecta
  [api-key term on-result-fn]
  (let [conn (establish-connection on-result-fn)]
    (.sendPacket conn (make-iq-packet api-key term conn))))
