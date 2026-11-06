import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Ramp-up to 20 virtual users
    { duration: '1m', target: 50 },  // Sustained load at 50 virtual users
    { duration: '30s', target: 0 },  // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<3000'], // 95% of requests under 1.5s
    http_req_failed: ['rate<0.01'],                  // Error rate < 1%
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  const token = __ENV.AUTH_TOKEN || '';
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. Health check & Readiness
  const healthRes = http.get(`${BASE_URL}/actuator/health/readiness`);
  check(healthRes, {
    'readiness status is 200': (r) => r.status === 200,
  });

  // 2. Keyword Search Query
  const searchRes = http.get(`${BASE_URL}/api/v1/search?query=policy&page=0&size=10`, { headers });
  check(searchRes, {
    'search status is 200': (r) => r.status === 200,
    'search latency < 300ms': (r) => r.timings.duration < 300,
  });

  // 3. Grounded Copilot Ask Query
  const askPayload = JSON.stringify({
    question: 'What is the company vacation and sick leave policy?',
    topK: 5,
  });
  const copilotRes = http.post(`${BASE_URL}/api/v1/copilot/ask`, askPayload, { headers });
  check(copilotRes, {
    'copilot status is 200': (r) => r.status === 200,
    'copilot returned answer': (r) => r.json('answer') !== undefined,
  });

  sleep(1);
}
