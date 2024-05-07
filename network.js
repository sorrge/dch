import * as random from "./random.js";
import * as safeNodes from "./nkn_safe_nodes.js";

// addresses are banned on a first-in-first-out basis for 5 minutes
class BannedAddrs {
    static maxBannedPeers = 100;
    
    constructor() {
        this.bannedAddrs = new Map();
    }

    addBannedAddr(addr) {
        if(this.bannedAddrs.has(addr))
            this.bannedAddrs.delete(addr); // remove the address from the banned list so it can be re-added with a new timestamp
        
        // record the time the address was banned
        this.bannedAddrs.set(addr, Date.now());
        while(this.bannedAddrs.size > BannedAddrs.maxBannedPeers) {
            const firstKey = this.bannedAddrs.keys().next().value;  // Get the first (oldest) key
            this.bannedAddrs.delete(firstKey);  // Delete the element with the first key        
        }
    }

    removeBannedAddr(addr) {
        this.bannedAddrs.delete(addr);
    }

    isBanned(addr) {
        if(!this.bannedAddrs.has(addr))
            return false;

        const bannedTime = this.bannedAddrs.get(addr);
        if(Date.now() - bannedTime > 5*60*1000) {
            this.bannedAddrs.delete(addr);
            return false;
        }

        return true;
    }
}


export class DchNet {
    static topicStr = "dch_v1";
    static topicHex = nkn.hash.sha256Hex(DchNet.topicStr);

    constructor(safeNodesOnly) {
        var generator = null;
        if(safeNodesOnly) {
            generator = safeNodes.safeNodeClientGenerator(safeNodesOnly);
        }
        else {
            generator = retryClientGenerator(basicClientGenerator, 3);
        }

        this.client = new RedundantClient(generator, 3, DchNet.topicHex);
        this.bannedAddrs = new BannedAddrs();
        this.prevDests = [];
        this.lastPing = null;
        this.mode = safeNodesOnly ? "safe" : "normal";

        this.client.onMessage((message) => {
            this.bannedAddrs.removeBannedAddr(message.src);
            if(!this.client.isMyAddr(message.src))
                this.lastPing = Date.now();
        });
    }

    async start() {
        await this.client.start();
    }

    async broadcast(message) {
        try {
            await this.client.publish(DchNet.topicHex, message);
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
                addr => !this.client.isMyAddr(addr) && !this.bannedAddrs.isBanned(addr));
        }
        catch (e) {
            console.log("Failed to get subscribers - using previous destinations. Error: " + e);
        }

        if(notBanned.length === 0)
            throw new Error("No neighbors to send to");
        
        this.prevDests = notBanned;

        const dests = random.sampleArray(notBanned, 3);
        const sendPromises = [];
        // lambda captures dest correctly
        for(const dest of dests) {
            sendPromises.push(this.client.sendWithAny(dest, message).catch(e => {
                console.log("Failed to send to neighbor " + dest);
                this.bannedAddrs.addBannedAddr(dest);
                throw e;
            }));
        }

        return Promise.any(sendPromises);
    }
}


function retryClientGenerator(baseGen, retries) {
    return async function() {
        for(let i = 0; i < retries; i++) {
            try {
                return await baseGen();
            }
            catch (e) {
                console.log("Client creation failed: " + e);
            }
        }

        throw new Error(`Failed to create client after ${retries} retries`);
    }
}


function basicClientGenerator() {
    return new Promise((resolve, reject) => {
        const client = new nkn.Client({identifier: random.seed256(), tls: true});
        client.onConnect(() => {
            resolve(client);
        });

        client.onConnectFailed((error) => {
            reject(error);
        });
    });
}


class RedundantClient {
    constructor(clientGenerator, targetClients, topic) {
        this.clientGenerator = clientGenerator;
        this.targetClients = targetClients;
        this.clients = [];

        this.onMessageListeners = [];
        this.topic = topic;
    }

    // this will start the clients and return a promise that resolves when at least one client is started
    // if all clients fail to start, the promise will reject
    async start() {
        const p = this.#fillRequredClients();
        return p.then(() => {
            // subscribe to the topic every 30 minutes
            setInterval(this.#subscribe.bind(this), 30*60*1000);

            // attempt to connect to the network every 5 minutes
            setInterval(this.#fillRequredClients.bind(this), 5*60*1000);
        });
    }

    onMessage(listener) {
        this.onMessageListeners.push(listener);
    }

    async publish(topic, message) {
        return this.#trySequentiallyInRandomOrder(client => client.publish(topic, message, {txPool: true}));
    }

    async sendWithAny(dest, message) {
        // send via one random client
        const client = this.clients[random.getRandomInt(this.clients.length)];
        await client.send(dest, message);
        return true;
    }

    async sendReply(dest, ourAddr, message) {
        for(const client of this.clients)
            if(client.addr === ourAddr)
                return await client.send(dest, message);

        throw new Error("Client not found");
    }

    async getSubscribers(topic) {
        return this.#trySequentiallyInRandomOrder(client => client.getSubscribers(topic, {txPool: true}));
    }

    isMyAddr(addr) {
        return this.clients.some(client => client.addr === addr);
    }

    async getNodeTimestamp() {
        var timestamp = null;
        await this.#trySequentiallyInRandomOrder(async client => {
            const addr = 'https://' + client.node.rpcAddr;
            const nodeState = await nkn.rpc.getNodeState({rpcServerAddr: addr});
            timestamp = nodeState.currTimeStamp * 1000;
        });

        if(timestamp === null)
            throw new Error("Failed to get timestamp");

        return timestamp;
    }
    
    async #subscribe() {
        try {
            const promises = [];
            for(const client of this.clients)
                promises.push(client.subscribe(this.topic, 100, client.identifier));

            await Promise.all(promises);
        }
        catch (e) {
            console.log("Some clients failed to subscribe: " + e);
        }
    }

    #newClient(client) {
        console.log("New client started: " + client.addr);
        this.clients.push(client);
        client.subscribe(this.topic, 100, client.identifier);
        client.onMessage((message) => {
            message.dest = client.addr;
            for (const listener of this.onMessageListeners)
                listener(message);
        });

        client.onWsError((error) => {
            this.clients = this.clients.filter(c => c !== client);
            client.close();
            if(this.clients.length < this.targetClients)
                this.clientGenerator().then(client => { this.#newClient(client); });
        })
    }

    async #trySequentiallyInRandomOrder(func) {
        const indexes = Array.from(Array(this.clients.length).keys());
        random.shuffleArray(indexes);

        for (const i of indexes) {
            try {
                return await func(this.clients[i]);
            }
            catch (e) {
            }
        }

        throw new Error("Failed to perform operation");
    }

    async #fillRequredClients() {
        const neededClients = this.targetClients - this.clients.length;
        if(neededClients <= 0)
            return;

        const promises = [];
        for(let i = 0; i < neededClients; i++)
            promises.push(this.clientGenerator().then(client => { this.#newClient(client); return true; }));

        await Promise.any(promises);
    }
}
