import test from 'node:test';
import assert from 'node:assert/strict';
import { buildTransferInstruction, computeUniqueCode } from './appPayment.js';

test('manual transfer instructions keep the exact amount with a valid unique code', () => {
  const result = buildTransferInstruction({
    bookingId: 'bk_123',
    amount: 750000,
    bankName: 'BCA',
    bankAccountNumber: '1234567890',
    bankAccountName: 'PT JepretAja Indonesia',
    expiresAtMs: 1_700_000_000_000,
  });

  assert.equal(result.status, 'awaiting_transfer');
  assert.equal(result.provider, 'transfer_manual');
  assert.ok(result.uniqueCode >= 1 && result.uniqueCode <= 999);
  assert.equal(result.transferAmount, 750000 + result.uniqueCode);
  assert.match(result.instruction, /kode unik/i);
});

test('unique code remains stable for the same booking id', () => {
  const first = computeUniqueCode('bk_123');
  const second = computeUniqueCode('bk_123');
  assert.equal(first, second);
  assert.ok(first >= 1 && first <= 999);
});
