import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080';
const EVENT_ID = 'b429ba59-059c-4971-9c8a-a4197faa402f';

export const options = {
    scenarios: {
        pessimistic: {
            executor: 'shared-iterations',
            exec: 'testPessimistic',
            vus: 50,
            iterations: 200,
            tags: { scenario: 'pessimistic' },
        },

        no_lock: {
            executor: 'shared-iterations',
            exec: 'testNoLock',
            vus: 50,
            iterations: 200,
            startTime: '10s',
            tags: { scenario: 'no_lock' },
        },

        atomic_update: {
            executor: 'shared-iterations',
            exec: 'testAtomicUpdate',
            vus: 50,
            iterations: 200,
            startTime: '20s',
            tags: { scenario: 'atomic_update' },
        },

        optimistic: {
            executor: 'shared-iterations',
            exec: 'testOptimistic',
            vus: 50,
            iterations: 200,
            startTime: '30s',
            tags: { scenario: 'optimistic' },
        },
    },
};

function generateSeats(startRow) {
    const seats = [];

    for (let row = startRow; row < startRow + 34; row++) {
        for (let col of ['A','B','C','D','E','F']) {
            seats.push(`${row}-${col}`);
            if (seats.length === 200) return seats;
        }
    }

    return seats;
}

// API별 좌석 pool
const pessimisticSeats = generateSeats(1);
const noLockSeats = generateSeats(40);
const atomicSeats = generateSeats(80);
const optimisticSeats = generateSeats(120);

function makePayload(seat) {
    return JSON.stringify({
        eventId: EVENT_ID,
        seatNumber: seat,
        userId: `user-${__VU}-${__ITER}-${Date.now()}`
    });
}

function postReservation(path, seat) {
    const url = `${BASE_URL}${path}`;

    const params = {
        headers: {
            "Content-Type": "application/json"
        }
    };

    const res = http.post(url, makePayload(seat), params);

    check(res, {
        "status >= 200": (r) => r.status >= 200
    });

    return res;
}

export function testPessimistic() {
    const seat = pessimisticSeats[__ITER];
    postReservation("/admin/scenarios/reservation/pessimistic", seat);
}

export function testNoLock() {
    const seat = noLockSeats[__ITER];
    postReservation("/admin/scenarios/reservation/no-lock", seat);
}

export function testAtomicUpdate() {
    const seat = atomicSeats[__ITER];
    postReservation("/admin/scenarios/reservation/atomic-update", seat);
}

export function testOptimistic() {
    const seat = optimisticSeats[__ITER];
    postReservation("/admin/scenarios/reservation/optimistic", seat);
}