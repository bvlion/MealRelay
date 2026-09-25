'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { IMAGE_ANALYSIS_MODEL, analyzeMealImage } = require('../src/imageMealAnalysis');

test('GPT-5.6 Luna parses multiple photographs together with confirmed values separate from estimates', async () => {
  let request;
  const client = {
    responses: {
      parse: async (value) => {
        request = value;
        return {
          output_parsed: {
            foodDisplayName: 'ご飯 約150g、焼き鮭 約80g',
            estimated: {
              energyKcal: 450,
              proteinGrams: 28,
              carbohydrateGrams: 62,
              fatGrams: 10,
            },
            confirmed: {
              energyKcal: { value: 430, origin: 'packageLabel' },
              proteinGrams: null,
              carbohydrateGrams: null,
              fatGrams: null,
            },
          },
        };
      },
    },
  };

  const analysis = await analyzeMealImage({
    client,
    images: [
      { data: Buffer.from('photo'), mediaType: 'image/jpeg' },
      { data: Buffer.from('side-dish'), mediaType: 'image/png' },
    ],
  });

  assert.equal(request.model, IMAGE_ANALYSIS_MODEL);
  assert.equal(request.reasoning, undefined);
  assert.equal(request.input[0].content[1].type, 'input_image');
  assert.equal(request.input[0].content[1].image_url,
    `data:image/jpeg;base64,${Buffer.from('photo').toString('base64')}`);
  assert.equal(request.input[0].content[2].image_url,
    `data:image/png;base64,${Buffer.from('side-dish').toString('base64')}`);
  assert.match(request.instructions, /shared platter/);
  assert.match(request.instructions, /actually consumed/);
  assert.equal(request.text.format.type, 'json_schema');
  assert.deepEqual(analysis.confirmed, {
    energyKcal: { value: 430, origin: 'packageLabel' },
  });
  assert.equal(analysis.estimated.energyKcal, 450);
});

test('an image analysis response without structured output is rejected', async () => {
  const client = { responses: { parse: async () => ({ output_parsed: null }) } };

  await assert.rejects(
    analyzeMealImage({
      client,
      images: [{ data: Buffer.from('photo'), mediaType: 'image/jpeg' }],
    }),
    /invalid result/,
  );
});
