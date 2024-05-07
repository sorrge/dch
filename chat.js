import {DchNet} from "./network.js";
import {PostsStorage} from "./posts-storage.js";
import * as safeNodes from "./nkn_safe_nodes.js";


var net = null;
const postsStorage = new PostsStorage();


async function messageReceivedCallback(message) {
    if(message.payload.length > PostsStorage.maxPostLength)
        return;

    const msgData = JSON.parse(message.payload);    
    if(msgData.command === "POST") {
        const post = msgData.post;
        postsStorage.addPost(post);
    }
    else if(msgData.command === "UPDATE") {
        console.log("UPDATE command received from " + message.src);
        const timestamp = msgData.timestamp;
        const posts = postsStorage.getPostsAfter(timestamp);
        if(posts.length > 0) {
            console.log("Sending " + posts.length + " posts to " + message.src);

            const promises = [];
            for(const post of posts) {
                const reply = JSON.stringify({command: "POST", post: {text: post.text, timestamp: post.timestamp}});
                promises.push(net.client.sendReply(message.src, message.dest, reply));
            }

            await Promise.all(promises).catch(e => console.log("Failed to send posts to " + message.src));
        }
        else if(msgData.numPosts < postsStorage.posts.size) {
            console.log(`${message.src} is missing some posts. Asking for sync...`);
            await net.client.sendReply(message.src, message.dest, JSON.stringify({command: "SEND-SYNC"}));
        }
    }
    else if(msgData.command === "SYNC") {
        console.log("SYNC command received from " + message.src);
        const existingIds = new Set(msgData.ids);

        const promises = [];
        for(const id of postsStorage.getAllIds()) {
            if(existingIds.has(id))
                continue;

            const post = postsStorage.getById(id);
            const reply = JSON.stringify({command: "POST", post: {text: post.text, timestamp: post.timestamp}});
            promises.push(net.client.sendReply(message.src, message.dest, reply));
        }

        if(promises.length > 0)
            console.log("Sending " + promises.length + " posts to " + message.src);

        await Promise.all(promises).catch(e => console.log("Failed to send posts to " + message.src));

        const missingIds = msgData.ids.filter(id => !postsStorage.posts.exists(id));
        if(missingIds.length > 0) {
            console.log("Missing " + missingIds.length + " posts from " + message.src + ". Sending SYNC request...");
            await net.client.sendReply(message.src, message.dest, JSON.stringify({command: "SYNC", ids: missingIds}));
        }
    }
    else if(msgData.command === "SEND-SYNC") {
        console.log("SEND-SYNC command received from " + message.src);
        await net.client.sendReply(message.src, message.dest, JSON.stringify({command: "SYNC", ids: postsStorage.getAllIds()}));
    }
    else {
        console.log("Message with unknown command received from " + message.src + ": " + message.payload);
    }
}


async function getNewMessages() {
    const latest = postsStorage.getLatestPost();
    try {
        await net.sendToNeighbors(JSON.stringify({command: "UPDATE", timestamp: latest ? latest.timestamp : 0, numPosts: postsStorage.posts.size}));
    }
    catch(e) {
        console.log("Failed to send update request: " + e);
    }
}


async function syncPosts() {
    const allIds = postsStorage.getAllIds();
    await net.sendToNeighbors(JSON.stringify({command: "SYNC", ids: allIds}));
}


async function setupChatInterface() {
    console.log("Setting up chat interface");

    document.getElementById("message-input").addEventListener("keypress", function(event) {
        if (event.key === "Enter") {
            event.preventDefault(); // Prevent form submission
            let message = this.value.trim();
            if (message) {
                net.broadcast(JSON.stringify({command: "POST", post: {text: message, timestamp: Date.now()}}));
                this.value = ""; // Clear input field
            }
        }
    });

    systemMessage("Connecting...");

    try {
        const nodes = await safeNodes.seedNodes();
        if(await safeNodes.canConnectToUnsafeNodes(nodes))
            net = new DchNet(null);
        else {
            systemMessage("Connecting to safe nodes only (may take a while)...");
            net = new DchNet(nodes);
        }

        await net.start();
        systemMessage("Loading posts...");
    }
    catch(e) {
        console.log("Failed to connect to the network: " + e);
        
        systemMessage("Failed to connect to the network. Refresh the page to try again.");
        return;
    }

    try {
        const timestamp = await net.client.getNodeTimestamp();
        const timeDiff = Math.abs(Date.now() - timestamp);
        if(timeDiff > 30000) {
            systemMessage(`Your system clock is not synchronized (${Math.round(timeDiff / 1000)} seconds off). `
                `The board will not work correctly. Please synchronize your system clock and refresh the page.`);

            return;
        }

        console.log(`Time difference ok (${timeDiff} ms off)`);
    }
    catch(e) {
        console.log("Failed to get node timestamp: " + e);
    }

    net.client.onMessage(messageReceivedCallback);        
    postsStorage.onPostAdded(showPost);
    postsStorage.onPostRemoved(removePost);

    // try to sync posts on startup repeatedly
    var postsSynced = false;
    for(let i = 0; i < 10; i++) {
        try {
            await syncPosts();
            console.log("Posts sync request sent successfully");
            postsSynced = true;
            systemMessage("You are not alone. Say hi!");
            setTimeout(() => systemMessage(null), 3000);
            break;
        }
        catch(e) {
            systemMessage(`Loading posts... (${i+1}/10)`)
            console.log("Failed to sync posts. Retrying..." + i);
        }
    }

    if(!postsSynced) {
        systemMessage("Failed to load posts. Are you the first one here?");
        setTimeout(() => systemMessage(null), 3000);
    }
    
    setInterval(() => {
        syncPosts().catch(e => console.log("Failed to sync posts: " + e));
    }, 5*60*1000);  // sync posts every 5 minutes

    // get new messages every 30 seconds
    setInterval(getNewMessages, 30*1000);

    updateStatus();
    setInterval(updateStatus, 5000);
}


function systemMessage(message, id="system-message") {
    var msgElement = document.getElementById(id);
    if (!msgElement) {
        if(message === null)
            return;

        msgElement = document.createElement("div");
        msgElement.id = id;
        msgElement.classList.add("top-bar");
        const logo = document.getElementById("logo");
        logo.parentNode.insertBefore(msgElement, logo.nextSibling);
    }

    if(message === null)
        msgElement.remove();
    else
        msgElement.textContent = message;
}


function scrollToBottom() {
    const html = document.documentElement;
    const body = document.body;

    const htmlHeight = html.scrollHeight;
    const bodyHeight = body.scrollHeight;

    const maxScrollTop = Math.max(htmlHeight, bodyHeight) - window.innerHeight;

    html.scrollTop = maxScrollTop;
    body.scrollTop = maxScrollTop;
}


function createPostElement(post) {
    const postElement = document.createElement("div");
    postElement.classList.add("post");

    const author = document.createElement("span");
    author.classList.add("author");
    author.textContent = "Frieren";
    postElement.appendChild(author);

    const time = new Date(Number(post.timestamp));
    const timeString = time.toLocaleString("en-US", { month: "short", day: "numeric", year: "numeric",
        hour: "2-digit", minute: "2-digit", second: "2-digit"});

    const timeElement = document.createElement("span");
    timeElement.classList.add("time");
    timeElement.textContent = timeString;
    postElement.appendChild(timeElement);

    postElement.appendChild(document.createElement("br"));

    const content = document.createElement("p");
    content.classList.add("content");
    content.textContent = post.text;
    postElement.appendChild(content);

    postElement.dataset.timestamp = Number(post.timestamp);
    postElement.dataset.id = post.id;
    return postElement;
}


function showPost(post) {
    const display = document.getElementById("chat-container");
    const postElement = createPostElement(post);

    // Find the correct position to insert the new message
    const messages = Array.from(display.children);
    const timestamp = Number(post.timestamp);
    const insertIndex = messages.findIndex(child => child.dataset.timestamp > timestamp);

    if (insertIndex === -1) {
        // No later message was found, append to the end
        display.appendChild(postElement);
    } else {
        // Insert before the first message that is later
        display.insertBefore(postElement, messages[insertIndex]);
    }

    // Scroll to the bottom of the page
    scrollToBottom();
}


function removePost(id) {
    const display = document.getElementById("chat-container");
    const postElement = display.querySelector(`[data-id="${id}"]`);
    if (!postElement)
        return;

    postElement.remove();
}


function updateStatus() {
    const statusElement = document.getElementById("status-bar");
    const secsSincePing = net.lastPing ? Math.round((Date.now() - net.lastPing) / 1000) : "?";
    statusElement.textContent = `${net.mode} - ${net.client.clients.length} peers - ${net.prevDests.length} addrs - ${secsSincePing}s since last ping`;
}


document.addEventListener("DOMContentLoaded", function() {
    setupChatInterface();
});
