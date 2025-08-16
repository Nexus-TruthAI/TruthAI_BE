package jpabasic.truthaiserver.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "prompts")
@Getter
@NoArgsConstructor
public class Prompt extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_prompt", nullable = false, columnDefinition = "Text")
    private String originalPrompt;

    @Column(name = "optimized_prompt", columnDefinition = "Text")
    private String optimizedPrompt;

    @Column(columnDefinition = "Text")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "prompt", cascade = CascadeType.ALL)
    private List<Answer> answers = new ArrayList<>();

    public Prompt(String originalPrompt,List<Answer> answers,User user) {
        this.originalPrompt = originalPrompt;
        this.answers = answers;
        this.user = user;
    }

    public void optimize(String optimizedPrompt) {
        this.optimizedPrompt = optimizedPrompt;
    }

    public void addAnswer(Answer answer) {
        answers.add(answer);
    }

    public void assignFolder(Folder folder) {
        this.folder = folder;
    }

}
