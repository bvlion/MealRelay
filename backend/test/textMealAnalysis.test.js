'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { analyzeTextMeal, TEXT_ANALYSIS_MODEL } = require('../src/textMealAnalysis');

test('GPT-5.6 Luna parses relative time and separates explicit nutrition from estimates', async () => {
  let request;
  const client = {
    responses: {
      parse: async (value) => {
        request = value;
        return {
          output_parsed: {
            foodDisplayName: 'カップ麺',
            eatenAt: '2026-09-20T20:00:00+09:00',
            estimated: {
              energyKcal: null,
              proteinGrams: 9,
              carbohydrateGrams: 55,
              fatGrams: 14,
            },
            confirmed: {
              energyKcal: { value: 351, origin: 'userInput' },
              proteinGrams: null,
              carbohydrateGrams: null,
              fatGrams: null,
            },
          },
        };
      },
    },
  };

  const analysis = await analyzeTextMeal({
    client,
    text: '昨日の夜 カップ麺 351kcal',
    inputAt: '2026-09-21T08:00:00+09:00',
  });

  assert.equal(request.model, TEXT_ANALYSIS_MODEL);
  assert.equal(request.reasoning, undefined);
  assert.match(request.instructions, /昨日の夜/);
  assert.match(request.input[0].content[0].text, /2026-09-21T08:00:00\+09:00/);
  assert.match(request.input[0].content[0].text, /カップ麺 351kcal/);
  assert.equal(request.text.format.type, 'json_schema');
  assert.equal(analysis.eatenAt, '2026-09-20T20:00:00+09:00');
  assert.deepEqual(analysis.confirmed, {
    energyKcal: { value: 351, origin: 'userInput' },
  });
  assert.deepEqual(analysis.estimated, {
    proteinGrams: 9,
    carbohydrateGrams: 55,
    fatGrams: 14,
  });
});

test('text analysis without structured output is rejected', async () => {
  const client = { responses: { parse: async () => ({ output_parsed: null }) } };

  await assert.rejects(
    analyzeTextMeal({
      client,
      text: '朝 トーストとヨーグルト',
      inputAt: '2026-09-21T08:00:00+09:00',
    }),
    /invalid result/,
  );
});
