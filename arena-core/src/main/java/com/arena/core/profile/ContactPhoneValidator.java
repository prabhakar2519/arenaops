package com.arena.core.profile;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ContactPhoneValidator implements ConstraintValidator<ContactPhone,String> {
 @Override public boolean isValid(String value,ConstraintValidatorContext context){
  if(value==null||value.isBlank())return true;
  String phone=value.replaceAll("[\\s().-]","");
  if(phone.matches("[6-9][0-9]{9}"))return true;
  return phone.matches("\\+[1-9][0-9]{7,14}")&&(!phone.startsWith("+91")||phone.matches("\\+91[6-9][0-9]{9}"));
 }
}
