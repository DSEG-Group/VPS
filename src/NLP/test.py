def maxScore(cardPoints, k):
    """
    :type cardPoints: List[int]
    :type k: int
    :rtype: int
    """
    min_sub = 9999
    n = len(cardPoints)
    window_size = n-k
    i = 0
    total = 0
    sum = 0
    for i in range (0,n):
        sum += cardPoints[i]
    total = sum
    i = 0
    j = i+window_size-1

    sum = 0
    for i in range (0,window_size):
        sum +=cardPoints[i]
    if min_sub>sum:
        min_sub = sum
    while j<n-1:
        sum -= cardPoints[i]
        sum += cardPoints[j+1]
        i = i+1
        j = j+1
        if(sum<min_sub):
            min_sub = sum
    
    return total - min_sub
            

if __name__ == "__main__":
    maxScore([1,2,3,4,5,6,1],3)