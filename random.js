export function shuffleArray(array) {
    for (let i = array.length - 1; i > 0; i--) {
        const j = getRandomInt(i + 1);
        [array[i], array[j]] = [array[j], array[i]];
    }
}


export function getRandomInt(max) {
    const array = new Uint32Array(1);
    window.crypto.getRandomValues(array);
    return Math.floor(array[0] / 4294967296 * max);
}


export function sampleArray(array, n) {
    if(n >= array.length)
        return array.slice(); // Return a copy of the array

    let result = array.slice(); // Create a copy of the array
    shuffleArray(result); // Shuffle the copy
    return result.slice(0, n); // Return the first n elements
}


export function seed256() {
    const array = new Uint32Array(8);
    window.crypto.getRandomValues(array);
    // Convert to hex
    return Array.from(array).map(x => x.toString(16).padStart(8, '0')).join('');
}
