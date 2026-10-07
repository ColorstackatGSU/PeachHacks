// MLH's verified schools list. Source: https://github.com/MLH/mlh-policies/blob/main/schools.csv
const SCHOOLS_URL = '/data/schools.json';

export const FEATURED_SCHOOL = 'Georgia State University';
export const MAX_RESULTS = 40;

// Only applied when the full name exists in the loaded list.
const ALIASES = {
  gsu: 'Georgia State University',
  gt: 'Georgia Institute of Technology',
  gatech: 'Georgia Institute of Technology',
  'georgia tech': 'Georgia Institute of Technology',
  ksu: 'Kennesaw State University',
  ggc: 'Georgia Gwinnett College',
  cau: 'Clark Atlanta University',
  uga: 'The University of Georgia',
  mit: 'Massachusetts Institute of Technology',
};

let schoolsPromise = null;

export function normalize(text) {
  return text
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

export function loadSchools() {
  if (!schoolsPromise) {
    schoolsPromise = fetch(SCHOOLS_URL)
      .then((response) => {
        if (!response.ok) throw new Error(`Schools list request failed with status ${response.status}`);
        return response.json();
      })
      .then((data) => {
        if (!Array.isArray(data?.schools) || data.schools.length === 0) {
          throw new Error('Schools list is empty');
        }
        const names = data.schools;
        return { names, keys: names.map(normalize), byKey: new Map(names.map((name) => [normalize(name), name])) };
      })
      .catch((error) => {
        // Let a later attempt retry instead of caching the failure.
        schoolsPromise = null;
        throw error;
      });
  }
  return schoolsPromise;
}

export function findExact(index, text) {
  return index.byKey.get(normalize(text)) ?? null;
}

export function searchSchools(index, query, limit = MAX_RESULTS) {
  const key = normalize(query);
  if (!key) {
    return { results: index.byKey.has(normalize(FEATURED_SCHOOL)) ? [FEATURED_SCHOOL] : [], total: 0 };
  }

  const tokens = key.split(' ');
  const buckets = [[], [], [], []];
  let total = 0;

  for (let i = 0; i < index.keys.length; i += 1) {
    const candidate = index.keys[i];
    let matches = true;
    for (let t = 0; t < tokens.length; t += 1) {
      if (!candidate.includes(tokens[t])) {
        matches = false;
        break;
      }
    }
    if (!matches) continue;

    total += 1;
    let rank = 3;
    if (candidate === key) rank = 0;
    else if (candidate.startsWith(key)) rank = 1;
    else if (candidate.includes(` ${key}`)) rank = 2;
    if (buckets[rank].length < limit) buckets[rank].push(index.names[i]);
  }

  const results = [];
  const alias = ALIASES[key];
  if (alias && index.byKey.has(normalize(alias))) results.push(alias);
  for (const bucket of buckets) {
    for (const name of bucket) {
      if (results.length >= limit) break;
      if (!results.includes(name)) results.push(name);
    }
  }

  return { results, total: Math.max(total, results.length) };
}
