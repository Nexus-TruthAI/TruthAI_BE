package jpabasic.truthaiserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "claims")
@Getter
@NoArgsConstructor
public class Claim {
    public Claim(String text, Float similarity, Answer answer) {
        this.text = text;
        this.similarity = similarity;
        this.answer = answer;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    private Float similarity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    private Answer answer;


    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL)
    private List<Source> sources = new ArrayList<>();
}