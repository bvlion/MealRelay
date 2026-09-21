'use strict';

const { TextMealError } = require('./textMealError');

function parseTextMealRequest(request) {
  const body = request.body;
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new TextMealError('JSON request body is required');
  }
  if (typeof body.text !== 'string' || !body.text.trim()) {
    throw new TextMealError('Meal text is required');
  }
  if (typeof body.inputAt !== 'string' || !body.inputAt) {
    throw new TextMealError('Input time is required');
  }
  if (typeof body.mealId !== 'string' || !body.mealId) {
    throw new TextMealError('Meal ID is required');
  }
  return {
    text: body.text.trim(),
    inputAt: body.inputAt,
    mealId: body.mealId,
  };
}

module.exports = { parseTextMealRequest };
