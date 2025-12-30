# Language and Subcategory Improvements

## Overview
This document describes the improvements made to address feedback about language support and subcategory text length.

## Changes Made

### 1. System Language Detection

Added dynamic system language detection that applies to all LLM-generated content:

**Supported Languages:**
- Japanese (ja)
- English (en)
- Korean (ko)
- Chinese (zh)
- Spanish (es)
- French (fr)
- German (de)
- Fallback: English

**Implementation:**
```kotlin
private fun getSystemLanguageName(): String {
    return when (Locale.getDefault().language) {
        "ja" -> "Japanese"
        "en" -> "English"
        "ko" -> "Korean"
        // ... other languages
        else -> "English" // fallback
    }
}
```

### 2. Language Applied to All LLM Operations

**Category Generation Prompt:**
```
Suggest a concise category name (1-3 words) for the following content.
Reply in [System Language] language.
...
```

**Subcategory Generation Prompt:**
```
Provide a very brief sub-category (1-3 words maximum) for the clipboard item.
Reply in [System Language] language.
...
```

**Category Merge Prompt:**
```
You are a categorization assistant.
Reply in [System Language] language.
...
```

**Summarizer API:**
Updated to use system language setting:
```kotlin
private fun getSummarizerLanguage(): SummarizerOptions.Language {
    return when (Locale.getDefault().language) {
        "ja" -> SummarizerOptions.Language.JAPANESE
        "ko" -> SummarizerOptions.Language.KOREAN
        else -> SummarizerOptions.Language.ENGLISH
    }
}
```

### 3. Shortened Subcategory Text

**Before:**
- Prompt: "Provide a short context phrase for the clipboard item."
- Result: Could produce relatively long phrases

**After:**
- Prompt: "Provide a very brief sub-category (1-3 words maximum) for the clipboard item."
- Additional instruction: "Reply with only 1-3 words, no explanation. Keep it extremely short and concise."
- Result: Forces LLM to generate very short subcategories

### 4. Simplified Implementation

**Design Philosophy:**
- Uses **only** the current system language
- No multi-language support or fallbacks
- No attempt to maintain both English and Japanese categories
- Users can re-analyze memos if they change system language

**Benefits:**
- Simpler implementation
- Reduced complexity
- Clear behavior
- Lower maintenance burden

## Impact on User Experience

### Before
- **Category/Subcategory**: Generated in English regardless of system language
- **Summary**: Generated in English (hardcoded)
- **Subcategory Length**: Could be long phrases (e.g., "A detailed description of...")
- **Multi-language**: Attempted to support multiple languages simultaneously

### After
- **Category/Subcategory**: Generated in user's system language
- **Summary**: Generated in user's system language
- **Subcategory Length**: Very short (1-3 words, e.g., "Landscape", "Recipe", "Meeting")
- **Single-language**: Uses only current system language

### Example Scenarios

#### Japanese System (ja):
- Image of sunset → Category: "自然", Subcategory: "風景", Summary: "海に沈む美しい夕日"
- Text about work → Category: "仕事", Subcategory: "会議", Summary: "Q3の財務報告書..."

#### English System (en):
- Image of sunset → Category: "Nature", Subcategory: "Landscape", Summary: "Beautiful sunset over ocean"
- Text about work → Category: "Work", Subcategory: "Meeting", Summary: "Q3 financial review..."

#### Korean System (ko):
- Image of sunset → Category: "자연", Subcategory: "풍경", Summary: "바다 위의 아름다운 일몰"
- Text about work → Category: "업무", Subcategory: "회의", Summary: "3분기 재무 검토..."

## Language Switching Behavior

**When User Changes System Language:**
1. Old memos retain their original language (no automatic translation)
2. New memos use the new system language
3. User can use "Re-analyze" button to regenerate categories in new language
4. "Re-analyze All Failures" batch operation also uses new language

**This is intentional:**
- Keeps implementation simple
- Avoids complexity of maintaining multi-language category mappings
- Users have control via re-analysis feature
- Clear and predictable behavior

## Technical Details

### Files Modified
1. **MlKitCategorizer.kt**
   - Added `getSystemLanguageName()` for prompt language
   - Added `getSummarizerLanguage()` for summarizer API
   - Updated `buildCategorizationPrompt()` to include language instruction
   - Updated `buildSubCategoryPrompt()` to enforce short output and language
   - Changed summarizer initialization to use system language

2. **CategoryMergeService.kt**
   - Added `getSystemLanguageName()` helper
   - Updated `buildPrompt()` to include language instruction

### Language Detection
- Uses `Locale.getDefault().language` to get system language code
- Maps ISO 639-1 codes (ja, en, ko, etc.) to full language names
- Falls back to English for unsupported languages

### ML Kit API Language Support
The summarizer uses ML Kit's built-in language support:
- `SummarizerOptions.Language.JAPANESE`
- `SummarizerOptions.Language.KOREAN`
- `SummarizerOptions.Language.ENGLISH`

For LLM prompts (category/subcategory), we use text instructions since the Generation API doesn't have explicit language parameters.

## Testing Recommendations

1. **Language Testing**:
   - Change device language to Japanese → Create memos → Verify Japanese output
   - Change to Korean → Create memos → Verify Korean output
   - Change to English → Create memos → Verify English output

2. **Subcategory Length**:
   - Create various memo types (images, text, URLs)
   - Verify subcategories are 1-3 words, not long phrases
   - Check both simple and complex content

3. **Re-analysis**:
   - Create memos in one language
   - Change system language
   - Re-analyze memos
   - Verify they get new categories in new language

4. **Edge Cases**:
   - Unsupported language (should fall back to English)
   - Very short content (should still get short subcategory)
   - Image with complex scene (subcategory should still be short)

## Migration Path

**For Existing Users:**
- No immediate change to existing memos
- Existing categories remain in their original language
- New memos use current system language
- Users can gradually re-analyze old memos if desired

**For New Users:**
- All memos generated in their system language from the start
- Consistent experience throughout

## Future Considerations

If multi-language support becomes necessary in the future:
1. Could store language code with each memo
2. Could add language preference setting (override system)
3. Could implement category translation mapping
4. Could support multiple languages simultaneously

However, the current single-language approach is recommended for simplicity and maintainability.
