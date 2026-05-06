# Gigsarathi WhatsApp MVP PRD

## 1. Document Control

| Field | Details |
| --- | --- |
| Product | Gigsarathi WhatsApp earning companion |
| Document type | Product Requirement Document |
| Version | 1.0 |
| Date | 2026-05-04 |
| Owner | Product Owner |
| Target release | MVP beta |
| Primary channel | WhatsApp |

## 2. Product Summary

Gigsarathi is a WhatsApp/Telegram(Bot and TMA)-first earning companion for gig workers. The MVP helps workers track daily earnings, understand real profit after fuel cost, receive simple next-day earning suggestions, and eventually qualify for financial products based on earnings history.

The product must feel fast, lightweight, and money-focused. It should not feel like a form or a generic chatbot. Most interactions should use buttons, quick replies, and predefined options. The first version should be rule-based and operationally simple, with a Google Sheets or lightweight database backend and WhatsApp API integration.

## 3. Problem Statement

Gig workers often know their gross daily earnings but do not consistently track real profit, fuel cost, best working hours, or high-demand zones. They also lack simple daily guidance that can help them earn more tomorrow.

If tracking takes too much effort, workers will stop responding within a few days. The product must deliver immediate value after each interaction.

## 4. Target Users

### Primary Persona: Food Delivery Worker

| Attribute | Details |
| --- | --- |
| Work type | Swiggy, Zomato, Zepto, or similar delivery apps |
| Motivation | Earn more per day, reduce wasted travel and fuel |
| Pain points | No simple profit tracking, uncertain best time/area to work, inconsistent income |
| Preferred UX | WhatsApp, button-based, short messages, Hindi-English friendly tone |

### Secondary Persona: Ride / Courier Worker

| Attribute | Details |
| --- | --- |
| Work type | Uber, Rapido, Porter, courier apps |
| Motivation | Maximize daily income and reduce low-profit trips |
| Pain points | Long-distance work can reduce profit, demand varies by zone/time |
| Preferred UX | Simple prompts, daily summary, peak-time nudges |

## 5. Product Goals

1. Help users track daily orders, earnings, fuel cost, and primary working zone in less than 30 seconds.
2. Show immediate value by calculating real daily profit after fuel cost.
3. Build a daily habit around end-of-day earnings capture.
4. Provide simple next-day earning guidance using heuristic zone and time scoring.
5. Create an earnings history that can support retention, referrals, and loan eligibility triggers.

## 6. Non-Goals For MVP

1. No advanced AI recommendation engine.
2. No long-form financial planning.
3. No complex app dashboard for workers.
4. No manual bank statement upload in MVP.
5. No multi-language personalization beyond simple Hindi-English tone guidelines.
6. No automated real-time location tracking unless explicitly added later.

## 7. Success Metrics

| Metric | Definition | MVP Target |
| --- | --- | --- |
| Onboarding completion rate | Users who answer work type, apps, and city | 70%+ |
| Day 1 tracking rate | Onboarded users who submit earnings on first day | 50%+ |
| 7-day tracking frequency | Average number of daily earnings submissions per user in first 7 days | 3+ days |
| Response time | Time user takes to complete daily earnings flow | Under 30 seconds |
| Tomorrow plan opt-in | Users who tap "Yes" after daily summary | 35%+ |
| Weekly report engagement | Users who tap "Yes" for weekly plan | 25%+ |
| Loan offer interest | Eligible users who tap "Check Offer" | 10%+ |
| Referral CTA engagement | Positive users who tap invite/share | 10%+ |

## 8. MVP Scope

### In Scope

1. First-time WhatsApp onboarding.
2. Daily earnings capture flow.
3. Daily profit summary.
4. Simple insight against user average.
5. Tomorrow earning plan based on hours and zone score.
6. Peak-time reminder before high-demand window.
7. Inactive user reactivation nudge.
8. Weekly report.
9. Referral prompt.
10. Loan eligibility entry point after 5-7 days of earnings data.
11. Admin-managed zone/time heuristic table.
12. Basic event and response tracking.

### Out of Scope

1. Full loan underwriting and disbursement workflow.
2. Worker-facing web or mobile app.
3. Automated event/weather ingestion.
4. Dynamic ML-based demand prediction.
5. Multi-city operations at scale beyond configured MVP cities.

## 9. User Experience Principles

1. Every message must be short.
2. Every flow must give a money-related output.
3. Avoid open text input wherever buttons can be used.
4. Keep all daily tracking questions answerable in under 30 seconds.
5. Use local, simple, money-focused language.
6. Avoid productivity jargon.
7. Do not ask too many questions before showing value.

## 10. Core User Flows

### Flow 1: First-Time Onboarding

**Trigger:** Immediately after signup or first WhatsApp opt-in.

**Objective:** Capture minimum worker profile needed to personalize tracking and recommendations.

**Steps:**

| Step | Prompt Goal | Input Type | Options | Required |
| --- | --- | --- | --- | --- |
| 1 | Identify work category | Quick reply | Food Delivery, Ride, Courier, Multiple | Yes |
| 2 | Identify gig apps | Multi-select or sequential quick reply | Swiggy, Zomato, Uber, Rapido, Zepto, Porter, Other | Yes |
| 3 | Capture city | Quick reply + text fallback | Auto-detect, Type manually | Yes |
| 4 | Confirm value proposition | Informational CTA | Track today | No |

**Acceptance Criteria:**

1. User can complete onboarding without typing, unless city manual entry is selected.
2. User profile is saved with work type, selected apps, city, and onboarding timestamp.
3. System sends a confirmation message explaining that the product will help track real earnings, best time, and best areas.
4. User can move directly into the daily tracking flow after onboarding.

### Flow 2: Daily Earnings Capture

**Trigger:** Daily between 9 PM and 11 PM local time.

**Objective:** Capture daily earning data and show immediate real-profit summary.

**Steps:**

| Step | Prompt Goal | Input Type | Options | Required |
| --- | --- | --- | --- | --- |
| 1 | Capture order volume | Quick reply | Less than 10, 10-15, 15-20, 20+ | Yes |
| 2 | Capture gross earnings | Quick reply | INR 0-500, INR 500-1000, INR 1000-1500, INR 1500+ | Yes |
| 3 | Capture fuel cost | Quick reply | INR 0-100, INR 100-200, INR 200+ | Yes |
| 4 | Capture primary zone | Quick reply | Configured city zones, plus Other | Yes |
| 5 | Show daily summary | Message | Earnings, fuel, real profit, insight | System |
| 6 | Ask for tomorrow plan | Quick reply | Yes, Skip | Optional |

**Acceptance Criteria:**

1. User can complete the flow using quick replies.
2. System stores one daily earning record per user per date.
3. If user submits multiple times in one day, system updates the latest daily record or asks whether to replace the existing record.
4. Real profit is calculated as gross earnings minus fuel cost.
5. System compares today with user average when enough history exists.
6. If there is no history, system still shows real profit and a simple encouragement or baseline message.

### Flow 3: Daily Earning Boost / Tomorrow Plan

**Trigger:** User taps "Yes" after daily summary.

**Objective:** Help user plan tomorrow with expected earnings, best time, and best zone.

**Steps:**

| Step | Prompt Goal | Input Type | Options | Required |
| --- | --- | --- | --- | --- |
| 1 | Capture intended work hours | Quick reply | 2, 4, 6, 8 | Yes |
| 2 | Calculate projected earnings | System | Formula-based | System |
| 3 | Recommend time and zone | System | Heuristic score | System |
| 4 | Send reminder commitment | Message | Peak reminder | System |

**Projected Earnings Formula:**

```
Estimated Earnings = Orders Per Hour x Average Payout x Planned Hours
```

**Initial assumptions:**

| Demand Level | Orders Per Hour | Avg Payout Range |
| --- | --- | --- |
| Low | 1.0-1.5 | INR 40-70 |
| Medium | 2.0-2.5 | INR 60-90 |
| High | 3.0-4.0 | INR 70-120 |

**Acceptance Criteria:**

1. System returns a projected earning range, not only a single number.
2. System recommends one best time slot and one or two best zones.
3. Recommendation uses the configured zone/time heuristic table.
4. Recommendation message remains under four short lines plus one tip.

### Flow 4: Peak-Time Nudges

**Trigger:** 30 minutes before configured peak time, for users who opted into tomorrow plan or have recent engagement.

**Objective:** Encourage users to log in during expected demand spikes.

**Acceptance Criteria:**

1. System sends a reminder before the highest-scored time slot.
2. Reminder includes estimated extra earning potential.
3. Reminder includes a single acknowledgement button.
4. Users who have opted out or are inactive beyond configured threshold do not receive repeated peak nudges.

### Flow 5: Inactive User Smart Nudge

**Trigger:** User has not responded for 2 consecutive days.

**Objective:** Bring users back by reminding them of previous earning value.

**Acceptance Criteria:**

1. System identifies users with no daily tracking response for 2 days.
2. Message references user's last tracked earning or profit when available.
3. Message includes one action button: Track Now.
4. User tapping Track Now resumes the daily earnings capture flow.

### Flow 6: Loan Entry Point

**Trigger:** User has 5-7 days of earnings data and meets basic eligibility rules.

**Objective:** Test user interest in small credit offers based on tracked earning behavior.

**MVP Eligibility Rules:**

| Rule | Requirement |
| --- | --- |
| Minimum tracking days | 5 submitted earning records |
| Recent activity | At least 3 submissions in last 7 days |
| Minimum average gross earnings | Configurable, default INR 700/day |
| User consent | Must tap Check Offer before any next step |

**Acceptance Criteria:**

1. Loan offer is only sent to eligible users.
2. User must explicitly tap Check Offer before seeing details.
3. Offer message clearly shows loan amount and repayment expectation.
4. Apply Now click is tracked as a high-intent lead.
5. MVP does not claim approval unless an actual loan partner flow exists.

### Flow 7: Weekly Report

**Trigger:** Every Sunday evening.

**Objective:** Summarize weekly earnings, fuel cost, profit, best day, and next-week opportunity.

**Acceptance Criteria:**

1. Weekly report is generated only when at least 2 earning records exist for the week.
2. Report includes total earnings, total fuel, real profit, best day, and one improvement insight.
3. User can request a plan for next week.
4. Weekly report data is saved for analytics.

### Flow 8: Referral Loop

**Trigger:** After positive engagement, such as weekly report interaction, 3+ tracking days, or high satisfaction signal.

**Objective:** Encourage users to invite similar workers.

**Acceptance Criteria:**

1. Referral prompt is not shown before the user receives clear value.
2. Referral link or share text contains a unique user referral code.
3. System tracks invite clicks and successful referred user onboarding.
4. Reward messaging is configurable and can be disabled if reward operations are not ready.

## 11. Recommendation Logic

### 11.1 Demand Signal

Demand signal is derived from the number of users reporting orders in a zone and time slot.

```
Demand = Count of users reporting orders in zone/time slot
```

### 11.2 Earnings Signal

Average earnings by zone/time slot:

```
Avg Earnings = Total reported earnings in zone/time slot / Number of users in zone/time slot
```

### 11.3 Supply Signal

Supply is the number of active workers reporting activity in the same zone/time slot.

```
Supply = Count of active workers in zone/time slot
```

### 11.4 Base Heuristic Score

Before enough user data exists, use manually configured zone/time rules.

| Zone Attribute | Demand Impact |
| --- | --- |
| Restaurant density | Higher food delivery demand |
| Office density | Lunch spike |
| Residential density | Dinner spike |
| Transit stations | Constant demand |
| Events, rain, festivals | Manual boost |

Example base table:

| Zone | Time Slot | Demand Score |
| --- | --- | --- |
| Bandra | Lunch | 8 |
| Bandra | Dinner | 9 |
| Andheri | Lunch | 7 |
| Kurla | Dinner | 6 |

### 11.5 Hotness Score

Once user data exists, combine demand, earnings, and supply:

```
Hotness Score = (Avg Earnings x Demand) / (Supply + 1)
```

**Interpretation:**

1. Higher average earnings improve score.
2. Higher demand improves score.
3. Higher worker supply reduces score.
4. Manual boosts can be added for rain, events, festivals, or known city patterns.

### 11.6 Recommendation Priority

The system should recommend:

1. Highest hotness score zone for the user's city and planned time.
2. If no user data exists, highest configured base heuristic score.
3. If scores are tied, choose the zone with lower supply.
4. If still tied, choose the zone closest to user's last reported zone if location is available.

## 12. Data Requirements

### 12.1 User Profile

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| user_id | String | Yes | Internal unique ID |
| whatsapp_number | String | Yes | Must be stored securely |
| work_type | Enum | Yes | Food Delivery, Ride, Courier, Multiple |
| apps_used | Array | Yes | Swiggy, Zomato, Uber, Rapido, Zepto, Porter, Other |
| city | String | Yes | Auto-detected or manually entered |
| preferred_language | String | No | Default Hindi-English |
| onboarding_status | Enum | Yes | Started, Completed |
| created_at | Timestamp | Yes | Signup time |
| last_active_at | Timestamp | Yes | Last interaction |

### 12.2 Daily Earnings Record

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| record_id | String | Yes | Internal unique ID |
| user_id | String | Yes | FK to user |
| date | Date | Yes | Local date |
| orders_range | Enum | Yes | Less than 10, 10-15, 15-20, 20+ |
| earnings_range | Enum | Yes | INR 0-500, 500-1000, 1000-1500, 1500+ |
| estimated_earnings_midpoint | Number | Yes | Used for calculations |
| fuel_range | Enum | Yes | INR 0-100, 100-200, 200+ |
| estimated_fuel_midpoint | Number | Yes | Used for calculations |
| estimated_profit | Number | Yes | Earnings midpoint minus fuel midpoint |
| zone | String | Yes | Primary work area |
| submitted_at | Timestamp | Yes | Response time |

### 12.3 Zone Heuristic Config

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| city | String | Yes | City name |
| zone | String | Yes | Zone name |
| time_slot | Enum | Yes | Breakfast, Lunch, Evening, Dinner, Late Night |
| base_demand_score | Number | Yes | 1-10 |
| restaurant_density_score | Number | No | 1-10 |
| office_density_score | Number | No | 1-10 |
| residential_density_score | Number | No | 1-10 |
| station_score | Number | No | 1-10 |
| manual_boost | Number | No | Default 0 |
| active | Boolean | Yes | Whether recommendation can use this row |

### 12.4 Event Tracking

| Event | Trigger | Required Properties |
| --- | --- | --- |
| onboarding_started | First interaction | user_id, timestamp |
| onboarding_completed | Profile complete | user_id, work_type, city |
| daily_prompt_sent | Daily capture message sent | user_id, date |
| daily_record_submitted | Daily capture completed | user_id, date, zone |
| daily_summary_viewed | Summary sent | user_id, profit |
| tomorrow_plan_requested | User taps Yes | user_id |
| peak_nudge_sent | Reminder sent | user_id, zone, time_slot |
| inactive_nudge_sent | Nudge sent | user_id |
| weekly_report_sent | Report sent | user_id, week_start |
| loan_offer_shown | Eligibility prompt sent | user_id |
| loan_offer_clicked | Check Offer clicked | user_id |
| referral_prompt_sent | Referral prompt sent | user_id |
| referral_clicked | Share clicked | user_id |

## 13. Admin And Operations Requirements

1. Admin can configure city zones and time-slot scores.
2. Admin can manually boost a zone for rain, event, festival, or local knowledge.
3. Admin can enable or disable loan offer prompts.
4. Admin can configure referral reward copy and reward amount.
5. Admin can view daily response counts by city, zone, and flow.
6. Admin can export daily earning records for analysis.
7. Admin can pause nudges if message quality or WhatsApp template approval issues occur.

## 14. WhatsApp Messaging Requirements

### Message Design Rules

1. Use quick replies wherever possible.
2. Use one question per message.
3. Keep messages below 350 characters where practical.
4. Avoid generic advice.
5. Use money-first language.
6. Use local tone, but keep MVP templates simple and approval-friendly.
7. Do not overuse emojis in production templates.

### Template Categories

| Template | WhatsApp Type | Approval Needed | Notes |
| --- | --- | --- | --- |
| Onboarding start | Utility / Service | Depends on opt-in flow | Can be session message if user initiates |
| Daily capture reminder | Utility | Likely yes | Scheduled outbound |
| Peak-time nudge | Utility / Marketing | Likely yes | Must respect opt-in |
| Inactive nudge | Marketing / Utility | Likely yes | Must include opt-out handling |
| Weekly report | Utility | Likely yes | User value message |
| Loan offer | Marketing / Financial | Yes | Requires careful compliance review |
| Referral prompt | Marketing | Yes | Requires reward terms |

## 15. Compliance And Risk Notes

1. Loan messaging must not imply guaranteed approval unless approved by lending partner.
2. User consent and opt-out handling are required for outbound nudges.
3. Financial data must be stored securely.
4. WhatsApp number and earning history should be treated as sensitive user data.
5. Loan eligibility rules must be transparent internally and auditable.
6. If using a Google Sheets backend, access must be restricted and logged.
7. Avoid collecting unnecessary personal data in MVP.

## 16. Edge Cases

| Scenario | Expected Behavior |
| --- | --- |
| User skips onboarding | Send one gentle reminder, then stop onboarding prompts until user re-engages |
| User selects Other app | Ask for app name only if needed; otherwise store Other |
| User manually types city | Match to configured cities where possible; otherwise store raw city and mark unsupported |
| User submits earnings twice | Update latest record or confirm replacement |
| User gives invalid text | Reply with quick-reply options again |
| User does not answer fuel cost | Send one reminder; if still missing, mark daily flow incomplete |
| No zone data available | Use base heuristic table |
| No weekly data | Do not send weekly report; send daily tracking reminder instead |
| Loan feature disabled | Suppress all loan prompts |
| User replies Stop | Opt user out of non-transactional nudges |

## 17. Milestones

### Milestone 1: MVP Foundation

**Goal:** Launch onboarding, daily tracking, basic storage, and daily summary.

**Exit Criteria:**

1. Users can onboard through WhatsApp.
2. Users can submit daily earnings.
3. System calculates real profit.
4. Admin can view/export daily records.
5. Basic events are tracked.

### Milestone 2: Recommendation Engine

**Goal:** Add heuristic zone/time scoring and tomorrow plan.

**Exit Criteria:**

1. Admin can manage zone/time score table.
2. Users can receive tomorrow plan after daily summary.
3. Projected earnings range is calculated.
4. Peak reminder is scheduled for opted-in users.

### Milestone 3: Retention Loops

**Goal:** Add inactive nudges and weekly report.

**Exit Criteria:**

1. Inactive users receive Track Now prompt after 2 days.
2. Weekly reports are generated every Sunday for qualified users.
3. Weekly plan CTA is tracked.

### Milestone 4: Monetization And Growth

**Goal:** Add loan entry point and referral loop.

**Exit Criteria:**

1. Loan eligibility trigger works for users with 5+ earning records.
2. Loan interest clicks are tracked.
3. Referral link/code is generated and tracked.
4. Reward copy can be configured or disabled.

## 18. Product Backlog With Effort Allocation

### Effort Scale

| Size | Meaning | Estimated Effort |
| --- | --- | --- |
| XS | Very small task | 0.5 day |
| S | Small task | 1 day |
| M | Medium task | 2-3 days |
| L | Large task | 4-6 days |
| XL | Complex task | 7-10 days |

### Team Roles

| Role | Responsibility |
| --- | --- |
| PM | Requirements, prioritization, acceptance review |
| UX | Conversation design and message copy |
| BE | Backend APIs, data model, scheduler, integrations |
| FE/Admin | Admin interface or internal tooling |
| QA | Test plans, regression, UAT |
| Ops | WhatsApp templates, city configuration, manual boosts |
| Compliance | Loan and financial messaging review |

### Task Breakdown

| ID | Epic | Task | Owner | Priority | Effort | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GS-001 | Product Setup | Finalize MVP scope, milestones, and launch city | PM | P0 | S | None | Scope, city, and launch assumptions signed off |
| GS-002 | Conversation Design | Write approved onboarding message copy | UX | P0 | S | GS-001 | Copy covers work type, apps, city, confirmation |
| GS-003 | Conversation Design | Write daily earnings capture copy | UX | P0 | S | GS-001 | Copy includes orders, earnings, fuel, zone, summary CTA |
| GS-004 | Data | Define user profile schema | BE | P0 | S | GS-001 | Schema supports WhatsApp number, work type, apps, city, status |
| GS-005 | Data | Define daily earnings schema | BE | P0 | S | GS-001 | Schema supports order, earning, fuel, zone, profit, date |
| GS-006 | Data | Define event tracking schema | BE | P0 | S | GS-001 | Required analytics events are captured |
| GS-007 | WhatsApp | Select WhatsApp API provider and integration approach | PM/BE | P0 | M | GS-001 | Provider selected and integration constraints documented |
| GS-008 | WhatsApp | Implement inbound webhook handler | BE | P0 | M | GS-007 | Incoming user replies are received and mapped to user session |
| GS-009 | WhatsApp | Implement outbound message sender | BE | P0 | M | GS-007 | System can send approved text and quick-reply messages |
| GS-010 | Onboarding | Build onboarding state machine | BE | P0 | M | GS-002, GS-008, GS-009 | User can complete onboarding end-to-end |
| GS-011 | Onboarding | Store profile responses | BE | P0 | S | GS-004, GS-010 | Profile data persists correctly |
| GS-012 | Daily Tracking | Build daily capture state machine | BE | P0 | L | GS-003, GS-005, GS-008, GS-009 | User can answer all daily tracking questions |
| GS-013 | Daily Tracking | Implement daily scheduled prompt | BE | P0 | M | GS-009, GS-012 | Prompt sends daily between 9 PM and 11 PM local time |
| GS-014 | Daily Tracking | Implement duplicate same-day handling | BE | P1 | S | GS-012 | Repeat submission updates or confirms replacement |
| GS-015 | Insights | Calculate estimated earnings midpoint | BE | P0 | S | GS-005 | Each earning range maps to a calculation value |
| GS-016 | Insights | Calculate estimated fuel midpoint | BE | P0 | S | GS-005 | Each fuel range maps to a calculation value |
| GS-017 | Insights | Calculate real profit summary | BE | P0 | S | GS-015, GS-016 | Summary shows gross, fuel, and profit |
| GS-018 | Insights | Compare today with user average | BE | P1 | M | GS-017 | Summary shows percentage difference when history exists |
| GS-019 | Admin Config | Create initial city/zone/time score table | Ops/PM | P0 | M | GS-001 | Launch city zones and time slots configured |
| GS-020 | Admin Config | Implement zone heuristic config storage | BE | P0 | M | GS-019 | Backend can read active heuristic rows |
| GS-021 | Admin Config | Build simple admin update path for scores | FE/Admin | P1 | M | GS-020 | Ops can update demand scores and manual boosts |
| GS-022 | Recommendation | Implement base heuristic recommendation | BE | P0 | M | GS-020 | System returns best zone/time using configured score |
| GS-023 | Recommendation | Implement hotness score formula | BE | P1 | M | GS-005, GS-020 | Formula uses avg earnings, demand, and supply |
| GS-024 | Recommendation | Implement projected earnings formula | BE | P0 | S | GS-022 | Projection uses OPH, payout, and planned hours |
| GS-025 | Tomorrow Plan | Build tomorrow plan flow | BE | P0 | M | GS-022, GS-024 | User receives best time, best zone, expected earning range |
| GS-026 | Peak Nudge | Build peak-time reminder scheduler | BE | P1 | M | GS-025 | Reminder sends 30 minutes before recommended peak |
| GS-027 | Retention | Define inactive user criteria | PM | P1 | XS | GS-012 | Inactivity rule documented |
| GS-028 | Retention | Build inactive user detector | BE | P1 | S | GS-027 | System identifies users inactive for 2 days |
| GS-029 | Retention | Build Track Now reactivation flow | BE | P1 | M | GS-028, GS-012 | Track Now resumes daily capture |
| GS-030 | Weekly Report | Define weekly summary logic | PM/BE | P1 | S | GS-005 | Logic covers totals, profit, best day, improvement insight |
| GS-031 | Weekly Report | Implement weekly report generator | BE | P1 | M | GS-030 | Weekly report generated for users with enough records |
| GS-032 | Weekly Report | Implement Sunday report scheduler | BE | P1 | S | GS-031, GS-009 | Report sends every Sunday evening |
| GS-033 | Loan | Define MVP loan eligibility rule | PM/Compliance | P1 | M | GS-005 | Eligibility is documented and approved |
| GS-034 | Loan | Implement eligibility detector | BE | P1 | M | GS-033 | Eligible users are flagged after 5+ records |
| GS-035 | Loan | Write loan offer and disclosure copy | UX/Compliance | P1 | M | GS-033 | Copy avoids guaranteed approval language |
| GS-036 | Loan | Build loan interest capture flow | BE | P1 | M | GS-034, GS-035 | Check Offer and Apply Now clicks are tracked |
| GS-037 | Referral | Define referral reward rules | PM/Ops | P2 | S | GS-001 | Reward and eligibility terms documented |
| GS-038 | Referral | Generate referral code/link | BE | P2 | M | GS-037 | Each user has a unique referral code |
| GS-039 | Referral | Build referral prompt | BE/UX | P2 | M | GS-038 | Prompt sends only after positive engagement |
| GS-040 | Analytics | Build basic funnel dashboard/export | BE/FE/Admin | P0 | M | GS-006 | Team can view onboarding, daily tracking, and plan opt-ins |
| GS-041 | QA | Write flow test cases | QA | P0 | M | GS-002, GS-003 | Test cases cover onboarding and daily tracking |
| GS-042 | QA | Test WhatsApp webhook and reply mapping | QA/BE | P0 | M | GS-008, GS-009 | Replies route to correct state |
| GS-043 | QA | Test scheduler behavior | QA/BE | P0 | M | GS-013, GS-026, GS-032 | Scheduled sends happen at configured times |
| GS-044 | QA | Test edge cases and invalid replies | QA | P1 | M | GS-010, GS-012 | Invalid and duplicate replies are handled |
| GS-045 | Launch Ops | Submit WhatsApp templates for approval | Ops | P0 | M | GS-002, GS-003, GS-035 | Required templates submitted and status tracked |
| GS-046 | Launch Ops | Prepare initial zone score operations sheet | Ops | P0 | S | GS-019 | Ops can update launch zone assumptions |
| GS-047 | Launch | Run internal UAT with test users | PM/QA/Ops | P0 | M | GS-010, GS-012, GS-025 | UAT issues triaged before beta |
| GS-048 | Launch | Launch beta to first cohort | PM/Ops | P0 | S | GS-047 | Cohort users onboarded and monitored |

## 19. Suggested Sprint Plan

### Sprint 0: Discovery And Setup

| Task IDs | Outcome |
| --- | --- |
| GS-001, GS-002, GS-003, GS-007, GS-019 | Final MVP scope, provider selection, message copy, launch zone assumptions |

### Sprint 1: Core Tracking MVP

| Task IDs | Outcome |
| --- | --- |
| GS-004 to GS-018, GS-040 to GS-042 | Onboarding, daily tracking, profit summary, basic analytics, QA coverage |

### Sprint 2: Recommendations And Retention

| Task IDs | Outcome |
| --- | --- |
| GS-020 to GS-032, GS-043, GS-044 | Heuristic recommendations, tomorrow plan, peak nudges, inactive nudges, weekly report |

### Sprint 3: Monetization, Referral, And Beta

| Task IDs | Outcome |
| --- | --- |
| GS-033 to GS-039, GS-045 to GS-048 | Loan interest capture, referral loop, WhatsApp templates, UAT, beta launch |

## 20. MVP Dependencies

| Dependency | Owner | Risk | Mitigation |
| --- | --- | --- | --- |
| WhatsApp API provider | PM/BE | Message templates and quick replies may vary by provider | Confirm provider capabilities before build |
| WhatsApp template approval | Ops | Outbound nudges may be delayed | Submit templates early; keep copy simple |
| City zone assumptions | PM/Ops | Poor recommendations may reduce trust | Start with conservative zones and manual boost controls |
| Data storage decision | BE | Google Sheets may not scale or secure sensitive data enough | Use Sheets only for pilot or choose lightweight DB |
| Loan compliance | Compliance | Financial claims can create regulatory and trust risk | Use eligibility language carefully; review before launch |

## 21. Open Questions

1. What is the first launch city?
2. Which WhatsApp API provider will be used: Twilio, Gupshup, Meta Cloud API, or another provider?
3. Should MVP use Google Sheets for speed or a lightweight production database from day one?
4. What exact zones should be supported for the launch city?
5. What is the source of initial restaurant, office, residential, and station density scoring?
6. What is the loan partner or manual lead handling process after Apply Now?
7. What referral reward is operationally feasible?
8. What opt-out wording is required by the WhatsApp provider and local compliance review?

## 22. Launch Readiness Checklist

| Area | Checklist Item | Status |
| --- | --- | --- |
| Product | MVP scope approved | Pending |
| Product | Launch city and zones finalized | Pending |
| UX | Onboarding copy approved | Pending |
| UX | Daily tracking copy approved | Pending |
| UX | Loan and referral copy approved | Pending |
| Engineering | WhatsApp provider integrated | Pending |
| Engineering | Core flows implemented | Pending |
| Engineering | Scheduler implemented | Pending |
| Engineering | Data storage secured | Pending |
| Analytics | Funnel events implemented | Pending |
| Ops | WhatsApp templates submitted | Pending |
| Ops | Zone score table configured | Pending |
| Compliance | Loan messaging reviewed | Pending |
| QA | End-to-end WhatsApp UAT passed | Pending |
| Launch | Beta cohort selected | Pending |

## 23. Acceptance Criteria For MVP Release

The MVP can be released to beta when:

1. A new user can complete WhatsApp onboarding without human intervention.
2. A user can submit daily earnings, fuel cost, and zone through buttons.
3. System calculates and sends real profit summary.
4. User can request a tomorrow plan and receive a recommended time, zone, and earning range.
5. Daily prompts and peak nudges can be scheduled reliably.
6. Inactive users can be nudged back into daily tracking.
7. Weekly reports are generated for users with sufficient data.
8. Admin or ops can update zone/time heuristic scores.
9. Core analytics events are tracked and exportable.
10. Loan and referral prompts can be enabled or disabled.
11. WhatsApp template and compliance requirements are satisfied.
12. QA has passed onboarding, daily tracking, recommendation, scheduler, inactive nudge, weekly report, loan, and referral scenarios.

## 24. Future Enhancements

1. Real-time demand map by zone.
2. Weather and event-based automatic boosts.
3. Personalized earnings targets.
4. App-specific recommendation logic.
5. Language personalization.
6. Bank or gig app statement upload.
7. Automated loan partner integration.
8. Worker dashboard for historical trends.
9. A/B testing for copy and nudge timing.
10. Machine learning model for demand prediction after enough data is collected.
