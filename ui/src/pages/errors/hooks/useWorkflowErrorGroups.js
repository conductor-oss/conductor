import { useMemo } from 'react';

// Function to calculate Levenshtein distance between two strings
const levenshteinDistance = (str1, str2) => {
  if (!str1) str1 = '';
  if (!str2) str2 = '';
  
  const m = str1.length;
  const n = str2.length;
  const dp = Array(m + 1).fill().map(() => Array(n + 1).fill(0));

  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (str1[i - 1] === str2[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1];
      } else {
        dp[i][j] = 1 + Math.min(
          dp[i - 1][j],     // deletion
          dp[i][j - 1],     // insertion
          dp[i - 1][j - 1]  // substitution
        );
      }
    }
  }
  return dp[m][n];
};

// Function to normalize text
const normalizeText = (text) => {
  if (!text) return '';
  return text
    .toLowerCase()
    .replace(/[0-9]+/g, 'N')  // Replace numbers with N
    .replace(/[^a-z\s]/g, ' ') // Replace special chars with space
    .replace(/\s+/g, ' ')      // Replace multiple spaces with single space
    .trim();
};

// Calculate similarity between two workflows based on both fields
const calculateWorkflowSimilarity = (workflow1, workflow2) => {
  const reason1 = normalizeText(workflow1.reasonForIncompletion);
  const reason2 = normalizeText(workflow2.reasonForIncompletion);
  const task1 = normalizeText(workflow1.failedReferenceTaskName);
  const task2 = normalizeText(workflow2.failedReferenceTaskName);

  // Calculate normalized distances for both fields
  const reasonDistance = levenshteinDistance(reason1, reason2) / 
    Math.max(reason1.length, reason2.length, 1);
  const taskDistance = levenshteinDistance(task1, task2) / 
    Math.max(task1.length, task2.length, 1);

  // Weight the distances (giving more weight to reason)
  return 0.7 * reasonDistance + 0.3 * taskDistance;
};

// Hierarchical clustering implementation
const hierarchicalClustering = (workflows, similarityThreshold = 0.3) => {
  if (!workflows || workflows.length === 0) return [];
  if (workflows.length === 1) return [{ members: [workflows[0]] }];

  // Initialize each workflow as its own cluster
  let clusters = workflows.map(workflow => ({
    members: [workflow]
  }));

  while (true) {
    let minDistance = Infinity;
    let mergeIndices = [-1, -1];

    // Find the two most similar clusters
    for (let i = 0; i < clusters.length; i++) {
      for (let j = i + 1; j < clusters.length; j++) {
        // Calculate average distance between all members of both clusters
        let totalDistance = 0;
        let comparisons = 0;

        // Replace nested forEach loops with regular for loops
        const cluster1 = clusters[i];
        const cluster2 = clusters[j];
        
        for (let m = 0; m < cluster1.members.length; m++) {
          for (let n = 0; n < cluster2.members.length; n++) {
            totalDistance += calculateWorkflowSimilarity(
              cluster1.members[m], 
              cluster2.members[n]
            );
            comparisons++;
          }
        }

        const avgDistance = totalDistance / comparisons;
        if (avgDistance < minDistance) {
          minDistance = avgDistance;
          mergeIndices = [i, j];
        }
      }
    }

    // Stop if minimum distance exceeds threshold
    if (minDistance > similarityThreshold || clusters.length <= 1) {
      break;
    }

    // Merge the two most similar clusters
    const [i, j] = mergeIndices;
    const newClusters = clusters.filter((_, index) => index !== i && index !== j);
    newClusters.push({
      members: [...clusters[i].members, ...clusters[j].members]
    });
    clusters = newClusters;
  }

  return clusters;
};

// Function to extract meaningful cluster name
const getClusterName = (cluster) => {
  if (!cluster.members.length) return 'Unknown';

  // Collect all words from both fields
  const words = cluster.members.flatMap(workflow => {
    const reason = normalizeText(workflow.reasonForIncompletion || '');
    const task = normalizeText(workflow.failedReferenceTaskName || '');
    return [...reason.split(' '), ...task.split(' ')];
  });

  // Count word frequencies
  const wordFreq = {};
  words.forEach(word => {
    if (word.length > 3) { // Only consider words longer than 3 characters
      wordFreq[word] = (wordFreq[word] || 0) + 1;
    }
  });

  // Get the most representative words
  const topWords = Object.entries(wordFreq)
    .sort(([,a], [,b]) => b - a)
    .slice(0, 2)
    .map(([word]) => word);

  if (topWords.length === 0) return 'Unknown';

  // Create cluster name
  const name = topWords
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');

  // Add task name if all members share the same task
  const commonTask = cluster.members.every(w => 
    w.failedReferenceTaskName === cluster.members[0].failedReferenceTaskName
  );
  
  if (commonTask && cluster.members[0].failedReferenceTaskName) {
    return `${name} (${cluster.members[0].failedReferenceTaskName})`;
  }

  return name;
};

// Export the filtering function
export const filterWorkflowsByReason = (workflows, selectedReason) => {
  if (!workflows || !selectedReason) return [];

  const clusters = hierarchicalClustering(workflows);
  const selectedCluster = clusters.find(cluster => 
    getClusterName(cluster) === selectedReason
  );

  return selectedCluster ? selectedCluster.members : [];
};

// Helper function to get consistent colors for error groups
const getColorForErrorGroup = (index) => {
  const colors = [
    '#ff4d4f', // red
    '#faad14', // yellow
    '#722ed1', // purple
    '#13c2c2', // cyan
    '#eb2f96', // pink
    '#52c41a', // green
    '#1890ff', // blue
    '#8c8c8c'  // grey
  ];
  return colors[index % colors.length];
};

// Main hook for workflow error groups
const useWorkflowErrorGroups = (workflows) => {
  return useMemo(() => {
    if (!workflows || workflows.length === 0) return [];

    // Perform hierarchical clustering
    const clusters = hierarchicalClustering(workflows);

    // Convert clusters to chart data format
    return clusters
      .map((cluster, index) => ({
        name: getClusterName(cluster),
        value: cluster.members.length,
        color: getColorForErrorGroup(index),
        tooltip: `${cluster.members.length} workflows`
      }))
      .filter(group => group.value > 0)
      .sort((a, b) => b.value - a.value);
  }, [workflows]);
};

export default useWorkflowErrorGroups; 