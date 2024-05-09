import * as random from "./random.js";


const rpcSeed = "https://mainnet-rpc-node-0001.nkn.org/mainnet/api/wallet";


async function getNeighbours(rpcUrl) {
    return await nkn.rpc.rpcCall(rpcUrl, "getneighbor", {})
}


class NKNNodes {
    constructor() {
        this.nodesSorted = new RBTree(function(a, b) { 
            const diff = BigInt("0x" + a) - BigInt("0x" + b);
            if (diff > 0)
                return 1;

            if (diff < 0)
                return -1;

            return 0;
        });

        this.nodes = new Map();
        this.bannedNodes = new Set();
    }

    add(node) {
        if (this.nodes.has(node.id) || this.bannedNodes.has(node.id))
            return false;

        this.nodes.set(node.id, node);
        this.nodesSorted.insert(node.id);
        return true;
    }

    remove(id) {
        if (!this.nodes.has(id))
            return false;

        this.nodes.delete(id);
        this.nodesSorted.remove(id);
        this.bannedNodes.add(id);
        return true;
    }

    find(clientId) {
        const closestAfter = this.nodesSorted.lowerBound(clientId);
        const closestBefore = closestAfter.prev();
        if(closestBefore === null)
            return this.nodes.get(this.nodesSorted.max());

        return this.nodes.get(closestBefore);
    }
}


export async function seedNodes() {
    const nodes = new NKNNodes();
    const seedNeighbors = await getNeighbours(rpcSeed);
    for (const node of seedNeighbors)
        nodes.add(node);

    return nodes;
}


function isNodeSafe(node) {
    return node.tlsWebsocketDomain && node.tlsWebsocketDomain.includes("staticdns3");
}


function findIdentifierForSafeNode(nodes, pubkey) {
    while (true) {
        const identifier = random.seed256();
        const addr = identifier + "." + pubkey;
        const clientId = nkn.hash.sha256(addr);
        const node = nodes.find(clientId);
        if (node && isNodeSafe(node))
            return identifier;
    }
}


async function findAndVerifySafeNode(nodes, pubkey) {
    const identifier = findIdentifierForSafeNode(nodes, pubkey);
    const addr = identifier + "." + pubkey;
    const clientId = nkn.hash.sha256(addr);
    const node = nodes.find(clientId);
    if (!node || !isNodeSafe(node))
        throw new Error("Failed to find a safe node");

    const nodeRpcAddr = `https://${node.tlsJsonRpcDomain}:${node.tlsJsonRpcPort}`;
    var wssAddr = null;
    try {
        wssAddr = await nkn.rpc.getWssAddr(addr, {rpcServerAddr: nodeRpcAddr});
    }
    catch(e) {
        nodes.remove(node.id);
        throw new Error("Failed to verify node: " + e);
    }

    if (!wssAddr.addr.includes("staticdns3")) {
        const neighbors = await getNeighbours(nodeRpcAddr);
        for (const neighbor of neighbors)
            nodes.add(neighbor);

        throw new Error(`Node is not safe. Client ID: ${clientId}, found node ID: ${node.id}, NKN assigned node ID: ${wssAddr.id}`);
    }

    return identifier;
}


async function findSafeNodeRepeatedly(nodes, pubkey, maxTries=50) {
    for (let i = 0; i < maxTries; i++) {
        if(!haveSafeNode(nodes))
            throw new Error("No safe nodes available");

        try {
            return await findAndVerifySafeNode(nodes, pubkey);
        }
        catch(e) {
            // console.log(`Failed to find a safe node: ${e}`);
        }
    }

    throw new Error("Failed to find a safe node");
}


export function waitForClientToConnect(client) {
    return new Promise((resolve, reject) => {
        client.onConnect(() => {
            resolve(client);
        });

        client.onConnectFailed((error) => {
            reject(error);
        });
    });
}


export function safeNodeClientGenerator(nodes) {
    return async function() {
        const key = new nkn.Key();
        const pubkey = key.publicKey;
        const identifier = await findSafeNodeRepeatedly(nodes, pubkey);
        const client = new nkn.Client({
            identifier: identifier,
            seed: key.seed});

        return await waitForClientToConnect(client);
    };
}


function haveSafeNode(nodes) {
    for (const node of nodes.nodes.values())
        if (isNodeSafe(node))
            return true;

    return false;
}


export async function canConnectToUnsafeNodes(nodes) {
    const unsafeNodes = Array.from(nodes.nodes.values()).filter(node => !isNodeSafe(node));
    const sample = random.sampleArray(unsafeNodes, 5);
    const promises = [];
    for (const node of sample) {
        const rpcAddr = `https://${node.tlsJsonRpcDomain}:${node.tlsJsonRpcPort}`;
        promises.push(nkn.rpc.getNodeState({rpcServerAddr: rpcAddr}));
    }

    try {
        await Promise.any(promises);
        return true;
    }
    catch(e) {
        return false;
    }    
}
