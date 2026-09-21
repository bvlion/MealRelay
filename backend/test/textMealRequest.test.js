'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { parseTextMealRequest } = require('../src/textMealRequest');

function requestFixture(body) {
  return { body };
}

test('JSON parser returns natural text, input time, and meal ID', () => {
  const parsed = parseTextMealRequest(requestFixture({
    text: '朝 トーストとヨーグルト',
    inputAt: '2026-09-21T08:00:00+09:00',
    mealId: 'text-1',
  }));

  assert.deepEqual(parsed, {
    text: '朝 トーストとヨーグルト',
    inputAt: '2026-09-21T08:00:00+09:00',
    mealId: 'text-1',
  });
});

test('JSON parser rejects a request without natural text', () => {
  assert.throws(
    () => parseTextMealRequest(requestFixture({
      inputAt: '2026-09-21T08:00:00+09:00',
      mealId: 'text-1',
    })),
    /Meal text is required/,
  );
});
