package com.jameati.opportunity;

import static com.jameati.opportunity.OpportunityDtos.*;
import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;

public final class OpportunitySpecifications {
  private OpportunitySpecifications() {}
  private static boolean has(String s){return s!=null&&!s.isBlank();}
  private static Expression<String> normalized(CriteriaBuilder cb, Expression<String> value) {
    Expression<String> result = cb.lower(value);
    for (String[] pair : new String[][]{{"أ","ا"},{"إ","ا"},{"آ","ا"},{"ٱ","ا"},{"ى","ي"},{"ة","ه"},{"ـ",""}})
      result = cb.function("replace", String.class, result, cb.literal(pair[0]), cb.literal(pair[1]));
    return result;
  }
  private static Predicate contains(CriteriaBuilder cb, Expression<String> value, String term) {
    String escaped = OpportunitySearchQueryParser.normalize(term).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return cb.like(normalized(cb, value), "%" + escaped + "%", '\\');
  }
  private static Predicate any(CriteriaBuilder cb, Expression<String> value, String... terms) {
    return cb.or(Arrays.stream(terms).map(term -> contains(cb,value,term)).toArray(Predicate[]::new));
  }
  private static Predicate country(CriteriaBuilder cb, Path<String> path, String country) {
    return switch(country) {
      case "الأردن" -> any(cb,path,"الأردن","Jordan");
      case "الولايات المتحدة" -> any(cb,path,"الولايات المتحدة","أمريكا","United States","USA");
      case "ألمانيا" -> any(cb,path,"ألمانيا","Germany");
      case "المملكة المتحدة" -> any(cb,path,"المملكة المتحدة","بريطانيا","United Kingdom","Britain","UK");
      case "تركيا" -> any(cb,path,"تركيا","Turkey","Türkiye");
      case "كندا" -> any(cb,path,"كندا","Canada");
      case "الإمارات" -> any(cb,path,"الإمارات","دبي","United Arab Emirates","UAE","Dubai");
      default -> contains(cb,path,country);
    };
  }
  private static Predicate category(CriteriaBuilder cb, Path<String> path, String category) {
    return switch(category) {
      case "منح دراسية" -> any(cb,path,"منح دراسية","منح","scholarship","study","برامج جامعية");
      case "تدريب" -> any(cb,path,"تدريب","training","internship");
      case "وظائف" -> any(cb,path,"وظائف","وظيفة","jobs","job");
      case "تطوع" -> any(cb,path,"تطوع","volunteer");
      default -> cb.equal(path,category);
    };
  }
  private static Predicate text(CriteriaBuilder cb, Root<Opportunity> root, String term) {
    return cb.or(contains(cb,root.get("title"),term),contains(cb,root.get("shortDescription"),term),
        contains(cb,root.get("description"),term),contains(cb,root.get("organization"),term),contains(cb,root.get("country"),term));
  }
  public static Specification<Opportunity> publicFilter(Filter f,int closingDays){return(root,q,cb)->{
    Predicate p=cb.and(cb.isFalse(root.get("deleted")),cb.isTrue(root.get("published")));
    var intent=OpportunitySearchQueryParser.parse(f.q());
    String selectedCountry=has(f.country())?f.country():intent.country();
    String selectedCategory=has(f.category())?f.category():intent.category();
    FundingType selectedFunding=f.funding()!=null?f.funding():intent.funding();
    String selectedDegree=has(f.degree())?f.degree():intent.degree();
    if(has(selectedCountry))p=cb.and(p,country(cb,root.get("country"),selectedCountry));
    if(has(selectedCategory))p=cb.and(p,category(cb,root.get("category"),selectedCategory));
    if(selectedFunding!=null)p=cb.and(p,cb.equal(root.get("funding"),selectedFunding));
    if(has(f.speciality())){Join<Opportunity,String> j=root.join("specialities");p=cb.and(p,cb.equal(j,f.speciality()));q.distinct(true);}
    if(has(f.nationality())){Join<Opportunity,String> j=root.join("nationalities");p=cb.and(p,cb.or(cb.equal(j,f.nationality()),cb.equal(j,"ALL")));q.distinct(true);}
    if(has(selectedDegree)){Join<Opportunity,String> j=root.join("degreeLevels");p=cb.and(p,contains(cb,j,selectedDegree));q.distinct(true);}
    if(intent.remote())p=cb.and(p,cb.or(any(cb,root.get("country"),"عن بعد","remote"),any(cb,root.get("title"),"عن بعد","remote"),any(cb,root.get("shortDescription"),"عن بعد","remote")));
    List<Predicate> flexible=new ArrayList<>();
    for(String field:intent.fields()){
      String[] terms=switch(field){
        case "programming" -> new String[]{"برمج","software","programming","developer","تطوير"};
        case "engineering" -> new String[]{"هندس","engineering"};
        case "medicine" -> new String[]{"طب","medical","medicine"};
        case "computer science" -> new String[]{"حاسوب","كمبيوتر","computer science"};
        case "artificial intelligence" -> new String[]{"ذكاء اصطناعي","artificial intelligence","AI"};
        default -> new String[]{"اعمال","business"};};
      List<Predicate> matches=new ArrayList<>();
      for(String term:terms)matches.add(text(cb,root,term));
      Join<Opportunity,String> j=root.join("specialities",JoinType.LEFT);q.distinct(true);
      for(String term:terms)matches.add(contains(cb,j,term));
      flexible.add(cb.or(matches.toArray(Predicate[]::new)));
    }
    if(!intent.keywords().isEmpty()){
      List<Predicate> matches=new ArrayList<>();
      for(String word:intent.keywords())matches.add(text(cb,root,word));
      flexible.add(cb.or(matches.toArray(Predicate[]::new)));
    }
    if(!flexible.isEmpty())p=cb.and(p,cb.or(flexible.toArray(Predicate[]::new)));
    if(f.status()!=null){var today=LocalDate.now();var automatic=cb.isNull(root.get("statusOverride"));p=switch(f.status()){case ALWAYS_OPEN->cb.and(p,automatic,cb.isTrue(root.get("alwaysOpen")));case UPCOMING->cb.and(p,cb.equal(root.get("statusOverride"),OpportunityStatus.UPCOMING));case EXPIRED->cb.and(p,automatic,cb.isFalse(root.get("alwaysOpen")),cb.lessThan(root.get("deadline"),today));case CLOSING_SOON->cb.and(p,automatic,cb.isFalse(root.get("alwaysOpen")),cb.between(root.get("deadline"),today,today.plusDays(closingDays)));case OPEN->cb.and(p,cb.or(cb.equal(root.get("statusOverride"),OpportunityStatus.OPEN),cb.and(automatic,cb.isFalse(root.get("alwaysOpen")),cb.greaterThan(root.get("deadline"),today.plusDays(closingDays)))));case UNKNOWN->cb.and(p,automatic,cb.isFalse(root.get("alwaysOpen")),cb.isNull(root.get("deadline")));};}
    return p;};}
}
