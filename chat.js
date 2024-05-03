const net = new DchNet();
const postsStorage = new PostsStorage();


async function generateSHA256Hash(input) {
    const encoder = new TextEncoder();
    const data = encoder.encode(input);
    const hashBuffer = await crypto.subtle.digest("SHA-256", data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(byte => byte.toString(16).padStart(2, "0")).join("");
    return hashHex;
}


const timeStampLength = 20;
function getFixedWidthTimestamp() {
    const timestamp = Date.now();  // Get current timestamp in milliseconds
    const fixedWidthTimestamp = timestamp.toString().padStart(timeStampLength, '0');
    return fixedWidthTimestamp;
}


function isTimestampValid(timestamp) {
    return timestamp.length === timeStampLength && !isNaN(Number(timestamp)) && new Date(Number(timestamp)) !== "Invalid Date";
}


async function messageReceivedCallback(message) {
    if(message.payload.length > PostsStorage.maxPostLength)
        return;

    const command = message.payload.substring(0, 4);
    if(command === "POST") {
        const timestamp = message.payload.substring(4, 4 + timeStampLength);
        if(message.payload.substring(4 + timeStampLength, 4 + timeStampLength + 1) !== "|" || !isTimestampValid(timestamp)) {
            console.log("Invalid POST message from " + message.src + ": " + message.payload);
            return;
        }

        const id = await generateSHA256Hash(message.payload.substring(4));
        const post = {
            src: message.src,
            text: message.payload.substring(4 + timeStampLength + 1),
            id: id,
            timestamp: timestamp,
            authorName: "Frieren"
        };

        postsStorage.addPost(post);
    }
    else if(command === "UPDT") {
        console.log("UPDT command received from " + message.src);
        const timestamp = message.payload.substring(4);
        const posts = postsStorage.getPostsAfter(timestamp);
        try {
            if(posts.length > 0)
                console.log("Sending " + posts.length + " posts to " + message.src);

            for(const post of posts) {
                net.client.send(message.src, "POST" + post.timestamp + "|" + post.text);
            }
        }
        catch(e) {
            console.log("Failed to send posts to " + message.src);
        }
    }
    else if(command === "SYNC") {
        console.log("SYNC command received from " + message.src);
        const ids = message.payload.substring(4);
        if(ids.length % 64 !== 0) {
            console.log("Invalid SYNC message from " + message.src);
            return;
        }

        const existingIds = new Set(ids.match(/.{64}/g));

        try {
            for(const id of postsStorage.getAllIds()) {
                if(existingIds.has(id))
                    continue;

                const post = postsStorage.getById(id);
                // console.log("Sending post to " + message.src + ": " + post.text);
                net.client.send(message.src, "POST" + post.timestamp + "|" + post.text);
            }
        }
        catch(e) {
            console.log("Failed to send post to " + message.src);
        }
    }
    else {
        console.log("Unknown command: " + command);
    }
}


function getNewMessages() {
    const latest = postsStorage.getLatestPost();
    net.sendToNeighbors("UPDT" + (latest ? latest : "0" * timeStampLength));
}


async function syncPosts() {
    const allIds = postsStorage.getAllIds().join("");
    const res = await net.sendToNeighbors("SYNC" + allIds);
    // check if any of the neighbors received the message
    return res && res.some(r => r.status === "success");
}


async function setupChatInterface() {
    document.getElementById("message-input").addEventListener("keypress", function(event) {
        if (event.key === "Enter") {
            event.preventDefault(); // Prevent form submission
            let message = this.value.trim();
            if (message) {
                net.broadcast("POST" + getFixedWidthTimestamp() + "|" + message);
                this.value = ""; // Clear input field
            }
        }
    });

    showPost({text: "Connecting...", timestamp: Date.now(), authorName: "System", id: "init-post"});

    if(await net.start())
        showPost({text: "Loading posts...", timestamp: Date.now(), authorName: "System", id: "init-post"});
    else {
        showPost({text: "Failed to connect to the network. Refresh the page to try again.",
            timestamp: Date.now(), authorName: "System", id: "init-post"});

        return;
    }

    net.client.onMessage(messageReceivedCallback);        
    postsStorage.onPostAdded(showPost);
    postsStorage.onPostRemoved(removePost);

    // try to sync posts on startup repeatedly
    for(let i = 0; i < 10; i++)
        if(await syncPosts()) {
            console.log("Posts sync request sent successfully");
            break;
        }
        else {
            const display = document.getElementById("chat-container");
            const postElement = display.querySelector(`[data-id="init-post"] p`);
            if (postElement)
                postElement.textContent += ".";
                    
            console.log("Failed to sync posts. Retrying..." + i);
        }
    
    setInterval(syncPosts, 5*60*1000);  // sync posts every 5 minutes

    // get new messages every 30 seconds
    setInterval(getNewMessages, 30*1000);
    
    // net.broadcast("POST" + getFixedWidthTimestamp() + "|Hello, world! " + net.client.addr.substring(0, 8));
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
    author.textContent = post.authorName;
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
    // delete the initial message with id="init_post"
    removePost("init-post");

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


document.addEventListener("DOMContentLoaded", function() {
    setupChatInterface();
});
