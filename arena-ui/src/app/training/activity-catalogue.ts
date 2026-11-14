import type { CatalogueItem } from './training.service';
export function activityCategory(item:CatalogueItem):string {
  const code=item.code;
  if(/BADMINTON|TENNIS|SQUASH|PICKLEBALL|PADEL|RACQUET/.test(code))return 'Racket sports';
  if(/CRICKET|FOOTBALL|SOCCER|BASKETBALL|VOLLEYBALL|HOCKEY|RUGBY|HANDBALL|KABADDI|KHO_KHO|BASEBALL|SOFTBALL|NETBALL/.test(code))return 'Team sports';
  if(/YOGA|FITNESS|GYM|PILATES|DANCE|ZUMBA|AEROBICS|ATHLETICS|RUNNING|CYCLING/.test(code))return 'Fitness & wellness';
  if(/KARATE|BOXING|JUDO|WRESTLING|TAEKWONDO|MARTIAL|KUNG|KICKBOX/.test(code))return 'Combat sports';
  if(/SWIM|WATER|ROWING|DIVING|SAILING/.test(code))return 'Aquatic sports';
  if(/SNOOKER|POOL|BILLIARD|CHESS|CARROM|ARCHERY|SHOOTING|BOWLING/.test(code))return 'Precision & indoor';
  return 'Other activities';
 }
export function activityIcon(item:CatalogueItem):string {
  const icons:Record<string,string>={CRICKET:'🏏',BADMINTON:'🏸',TENNIS:'🎾',TABLE_TENNIS:'🏓',FOOTBALL:'⚽',BASKETBALL:'🏀',VOLLEYBALL:'🏐',BASEBALL:'⚾',SOFTBALL:'🥎',HOCKEY:'🏑',SWIMMING:'🏊',BOXING:'🥊',KICKBOXING:'🥊',KARATE:'🥋',JUDO:'🥋',TAEKWONDO:'🥋',ARCHERY:'🏹',CHESS:'♟',SNOOKER:'🎱',POOL_BILLIARDS:'🎱',CYCLING:'🚴',ATHLETICS:'🏃',SKATING:'⛸',YOGA:'🧘',GYMNASTICS:'🤸',WEIGHTLIFTING:'🏋',GOLF:'⛳',RUGBY:'🏉'};
  return icons[item.code]||(item.code?item.label.split(/\s+/).map(word=>word[0]).join('').slice(0,2).toUpperCase():'◇');
 }
