// peers are banned on a first-in-first-out basis for 5 minutes
class BannedPeers {
    static maxBannedPeers = 100;
    
    constructor() {
        this.bannedPeers = new Map();
    }

    addBannedPeer(peer) {
        if(this.bannedPeers.has(peer))
            this.bannedPeers.delete(peer); // remove the peer from the banned list so it can be re-added with a new timestamp
        
        // record the time the peer was banned
        this.bannedPeers.set(peer, Date.now());
        while(this.bannedPeers.size > BannedPeers.maxBannedPeers) {
            const firstKey = this.bannedPeers.keys().next().value;  // Get the first (oldest) key
            this.bannedPeers.delete(firstKey);  // Delete the element with the first key        
        }
    }

    removeBannedPeer(peer) {
        this.bannedPeers.delete(peer);
    }

    isBanned(peer) {
        if(!this.bannedPeers.has(peer))
            return false;

        const bannedTime = this.bannedPeers.get(peer);
        if(Date.now() - bannedTime > 5*60*1000) {
            this.bannedPeers.delete(peer);
            return false;
        }

        return true;
    }
}


class DchNet {
    static topicStr = "dch_v1";
    static topicHex = null;

    constructor() {
        this.client = null;
        this.onReceivedListeners = [];
        this.bannedPeers = new BannedPeers();
        this.prevDests = [];
    }

    async start() {
        if(!await this.#tryConnectRepeatedly(10))
            return false;

        DchNet.topicHex = await generateSHA256Hash(DchNet.topicStr);
        // Subscribe to the topic evety 30 minutes for 30 minutes
        this.#subscribe30Min();
        setInterval(this.#subscribe30Min.bind(this), 30*60*1000);
        this.client.onMessage((message) => { this.bannedPeers.removeBannedPeer(message.src); });
        return true;
    }

    async broadcast(message) {
        try {
            await this.client.publish(DchNet.topicHex, message, {txPool: true});
            console.log("Broadcast successful");
        }
        catch (e) {
            console.log("Broadcast failed: " + e);
        }
    }

    async sendToNeighbors(message) {
        var notBanned = this.prevDests;
        try {
            const subs = await this.client.getSubscribers(DchNet.topicHex, {txPool: true});
            notBanned = subs.subscribers.concat(subs.subscribersInTxPool).filter(
                peer => peer != this.client.addr && !this.bannedPeers.isBanned(peer));
        }
        catch (e) {
            console.log("Failed to get subscribers - using previous destinations. Error: " + e);
        }

        if(notBanned.length === 0)
            return;
        
        this.prevDests = notBanned;

        const dests = this.#sampleArraySecurely(notBanned, 3);
        const sendPromises = dests.map(dest => {
            return this.client.send(dest, message)
                .then(() => {
                    console.log("Sent to neighbor " + dest);
                    return { dest, status: 'success' };
                })
                .catch(e => {
                    console.log("Failed to send to neighbor " + dest);
                    this.bannedPeers.addBannedPeer(dest);
                    return { dest, status: 'failed' };
                });
        });

        return Promise.all(sendPromises);
    }

    #waitForConnection() {
        return new Promise((resolve, reject) => {
            this.client.onConnect(() => {
                resolve();  // Resolve the promise when the client connects
            });

            this.client.onConnectFailed((error) => {
                reject(error);  // Reject the promise if there's an error
            });
        });
    }    

    async #connectClient(tls) {
        try {
            this.client = new nkn.MultiClient({tls: tls});
        }
        catch (e) {
            return false;
        }
    
        try {
            await this.#waitForConnection();
            return true;
        }
        catch (e) {
            return false;
        }
    }  
    
    async #tryConnectSecureOrNot() {
        if(await this.#connectClient(false))
            return true;
    
        if(await this.#connectClient(true))
            return true;
    
        return false;
    }

    async #tryConnectRepeatedly(tries) {
        for (let i = 0; i < tries; i++) {
            if(await this.#tryConnectSecureOrNot())
                return true;
        }
    
        return false;
    }    

    #subscribe30Min() {
        this.client.subscribe(DchNet.topicHex, 100);
        console.log('Subscribed to topic: ' + DchNet.topicStr);
    }    

    #getRandomInt(max) {
        const array = new Uint32Array(1);
        window.crypto.getRandomValues(array);
        return array[0] % max;
    }
    
    #sampleArraySecurely(array, n) {
        if(n >= array.length)
            return array.slice(); // Return a copy of the array

        let result = array.slice(); // Create a copy of the array
        for (let i = 0; i < n; i++) {
            const j = i + this.#getRandomInt(array.length - i); // Get a random index from i to array.length - 1
            [result[i], result[j]] = [result[j], result[i]]; // Swap elements
        }
        return result.slice(0, n); // Return the first n elements
    }
}
