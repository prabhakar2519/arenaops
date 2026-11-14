import {setup} from './test-business-profile.mjs';
import {test} from 'node:test';
import assert from 'node:assert/strict';

test('AUDIT email malformed addresses block the save form',()=>{for(const email of ['missing-at','owner@','owner name@example.test']){const {value,injector}=setup();value.edit();value.draft.publicContactEmail=email;assert.equal(value.validate(),false,email);assert.ok(value.fieldErrors.publicContactEmail);injector.destroy();}});
test('AUDIT email with consecutive local dots should match backend rejection',()=>{const {value,injector}=setup();try{value.edit();value.draft.publicContactEmail='owner..name@example.test';assert.equal(value.validate(),false,'Frontend currently accepts an email that backend @Email rejects');}finally{injector.destroy();}});
test('AUDIT Indian postal code must match country format',()=>{const {value,injector}=setup();try{value.edit();value.draft.country='India';value.draft.postalCode='ABCDEF';assert.equal(value.validate(),false,'Frontend currently checks only postal length');}finally{injector.destroy();}});
test('AUDIT valid email and optional blank contacts remain allowed',()=>{for(const email of ['owner@example.test','first.last+club@example.test','']){const {value,injector}=setup();value.edit();value.draft.publicContactEmail=email;value.draft.publicPhone='';assert.equal(value.validate(),true);injector.destroy();}});
