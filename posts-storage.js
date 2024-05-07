class PriorityQueue {
    constructor(maxSize) {
        this.maxSize = maxSize;
        this.elements = new Map(); // Store elements by ID
        this.priorityList = []; // Array to maintain order based on priority
        this.onElementRemovedListeners = [];
    }

    add(id, element, priority) {
        // Check if the element is already present
        if (this.elements.has(id))
            return false;

        // Check and maintain the max size
        if (this.priorityList.length >= this.maxSize) {
            // Remove the element with the lowest priority (first in the array)
            const lowest = this.priorityList.shift();
            this.elements.delete(lowest.id);
            for (const listener of this.onElementRemovedListeners)
                listener(lowest.id);
        }

        // Add the new element
        const newElement = { id, element, priority };
        this.elements.set(id, newElement);
        const index = this.priorityList.findIndex(el => el.priority > priority);
        if (index === -1)
            this.priorityList.push(newElement);
        else
            this.priorityList.splice(index, 0, newElement);

        return true;
    }

    getTop() {
        return this.priorityList[this.priorityList.length - 1];
    }

    getAll() {
        return this.priorityList.slice(); // Return a shallow copy of the sorted list
    }

    getAllAfter(priority) {
        return this.priorityList.filter(el => el.priority > priority);
    }

    exists(id) {
        return this.elements.has(id);
    }

    getById(id) {
        return this.elements.get(id).element;
    }

    onElementRemoved(listener) {
        this.onElementRemovedListeners.push(listener);
    }

    get size() {
        return this.priorityList.length;
    }
}



export class PostsStorage {
    static maxPosts = 200;
    static maxPostLength = 10000;

    constructor() {
        this.posts = new PriorityQueue(PostsStorage.maxPosts);
        this.onPostAddedListeners = [];
    }

    addPost(post) {
        if(post.text.length > PostsStorage.maxPostLength)
            return;

        // check that the post has required fields and only those fields
        if(!post.text || !post.timestamp || Object.keys(post).length !== 2) {
            return;
        }

        // check that the timestamp is a number and is not more than 30s in the future
        if(isNaN(Number(post.timestamp)) || Number(post.timestamp) > Date.now() + 30000) {
            return;
        }

        post.id = nkn.hash.sha256Hex(post.text + "|" + post.timestamp);        
        
        if(this.posts.add(post.id, post, Number(post.timestamp)))
            for(const listener of this.onPostAddedListeners)
                listener(post);
    }

    getPostsAfter(timestamp) {
        return this.posts.getAllAfter(Number(timestamp)).map(el => el.element);
    }

    onPostAdded(listener) {
        this.onPostAddedListeners.push(listener);
    }

    onPostRemoved(listener) {
        this.posts.onElementRemoved(listener);
    }

    getLatestPost() {
        return this.posts.size === 0 ? null : this.posts.getTop().element;
    }

    getAllIds() {
        return this.posts.getAll().map(el => el.id);
    }

    getById(id) {
        return this.posts.getById(id);
    }
}
