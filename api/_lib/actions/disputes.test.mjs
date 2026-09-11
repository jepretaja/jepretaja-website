import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateDisputeAmounts } from './disputes.js';

test('full refund consumes pending escrow without crediting creator', () => {
  assert.deepEqual(calculateDisputeAmounts(100000, 100000, 100000), {
    toCreator: 0,
    pendingAfter: 0,
    creatorFromPending: 0,
    customerFromPending: 100000,
  });
});

test('partial dispute splits pending escrow correctly', () => {
  assert.deepEqual(calculateDisputeAmounts(100000, 100000, 25000), {
    toCreator: 75000,
    pendingAfter: 0,
    creatorFromPending: 75000,
    customerFromPending: 25000,
  });
});

test('legacy insufficient pending balance never mints creator funds', () => {
  assert.deepEqual(calculateDisputeAmounts(100000, 30000, 0), {
    toCreator: 100000,
    pendingAfter: 0,
    creatorFromPending: 30000,
    customerFromPending: 0,
  });
});
