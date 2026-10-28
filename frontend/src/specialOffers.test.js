import test from 'node:test';
import assert from 'node:assert/strict';
import { isAvailableOffer } from './specialOffers.js';

const offer = { available: true, quantity: 10, pricePerUnit: 100, discountedPricePerUnit: 80, discountPercent: 20 };
test('available seller discounts are eligible for special offers', () => {
  assert.equal(isAvailableOffer(offer), true);
  assert.equal(isAvailableOffer({ ...offer, quantity: '0.5', discountPercent: '20' }), true);
});
test('sold-out, withdrawn, and undiscounted products are excluded', () => {
  for (const change of [{ quantity: 0 }, { quantity: -1 }, { available: false }, { discountPercent: 0 }, { discountPercent: null }]) {
    assert.equal(isAvailableOffer({ ...offer, ...change }), false);
  }
});
test('an offer needs an actual price reduction, including after currency rounding', () => {
  assert.equal(isAvailableOffer({ ...offer, discountedPricePerUnit: null }), false);
  assert.equal(isAvailableOffer({ ...offer, pricePerUnit: null }), false);
  assert.equal(isAvailableOffer({ ...offer, pricePerUnit: 0.01, discountedPricePerUnit: 0.01 }), false);
  assert.equal(isAvailableOffer({ ...offer, discountedPricePerUnit: 110 }), false);
});
